package config;

import com.example.authapp.models.Cart;
import com.example.authapp.network.SupabaseHttp;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Instant;
import java.util.Objects;

/** Atomic in-memory session. Network calls never hold the state lock used by logout. */
public final class SessionManager {
    private static final Object STATE_LOCK = new Object();
    private static final Object REFRESH_LOCK = new Object();
    private record State(long epoch, String accessToken, String refreshToken, String email, String userId,
                         boolean admin, boolean manager, boolean blocked, boolean confirmation, Instant expiresAt) {
        private static State signedOut(long epoch) { return new State(epoch,null,null,null,null,false,false,false,false,Instant.EPOCH); }
    }
    public record SessionLease(long epoch, String userId) {}
    private static volatile State state = State.signedOut(0);
    private SessionManager() {}
    public static String getAccessToken() { return state.accessToken(); }
    public static String getUserEmail() { return state.email(); }
    public static String getUserId() { return state.userId(); }
    public static boolean isAdmin() { return state.admin(); }
    public static boolean isManager() { return state.manager(); }
    public static boolean isBlocked() { return state.blocked(); }
    public static boolean isRegistrationAwaitingConfirmation() { return state.confirmation(); }

    public static SessionLease captureSession() { State current=state; return new SessionLease(current.epoch(),current.userId()); }
    public static SessionLease requireSession() {
        synchronized(STATE_LOCK) {
            if(state.userId()==null || state.accessToken()==null) throw new IllegalStateException("Войдите в аккаунт");
            return new SessionLease(state.epoch(),state.userId());
        }
    }
    public static SessionLease captureForToken(String token) {
        synchronized(STATE_LOCK) {
            if(state.userId()==null || !Objects.equals(state.accessToken(),token)) throw changed();
            return new SessionLease(state.epoch(),state.userId());
        }
    }
    public static void assertCurrent(SessionLease lease) { synchronized(STATE_LOCK) { check(lease); } }
    public static String tokenFor(SessionLease lease) {
        synchronized(STATE_LOCK) { check(lease); if(state.accessToken()==null)throw changed(); return state.accessToken(); }
    }
    /** Only short local mutations belong here, never network or other blocking work. */
    public static void runIfCurrent(SessionLease lease,Runnable action) {
        synchronized(STATE_LOCK) { check(lease); action.run(); }
    }
    private static void check(SessionLease lease) {
        if(lease==null || state.epoch()!=lease.epoch() || !Objects.equals(state.userId(),lease.userId())) throw changed();
    }
    private static IllegalStateException changed() { return new IllegalStateException("Сессия изменилась. Повторите действие в текущем аккаунте."); }
    public static void clearSession() {
        synchronized(STATE_LOCK) { clearLocked(); }
    }
    private static long clearLocked() {
        state=State.signedOut(state.epoch()+1);
        Cart.getInstance().clear();
        return state.epoch();
    }
    private static long beginAuthentication() { synchronized(STATE_LOCK) { return clearLocked(); } }
    private static void clearIfCurrent(long epoch) { synchronized(STATE_LOCK) { if(state.epoch()==epoch)clearLocked(); } }

    public static boolean login(String email,String password) {
        long epoch=beginAuthentication();
        try {
            JSONObject result=auth("/auth/v1/token?grant_type=password",new JSONObject().put("email",email).put("password",password));
            if(!acceptSession(epoch,result,email,false))return false;
            SessionLease lease=new SessionLease(epoch,result.getJSONObject("user").getString("id"));
            loadProfile(lease);
            synchronized(STATE_LOCK) { check(lease); if(state.blocked()){clearLocked();return false;} }
            return true;
        } catch(Exception ex) { clearIfCurrent(epoch); return false; }
    }
    public static boolean register(String email,String password) {
        long epoch=beginAuthentication();
        try {
            JSONObject result=auth("/auth/v1/signup",new JSONObject().put("email",email).put("password",password));
            if(acceptSession(epoch,result,email,false))return true;
            synchronized(STATE_LOCK) {
                if(state.epoch()!=epoch)return false;
                boolean confirmation=result.has("id")||result.has("user");
                state=new State(epoch,null,null,null,null,false,false,false,confirmation,Instant.EPOCH);
                return confirmation;
            }
        } catch(Exception ex) { clearIfCurrent(epoch);return false; }
    }
    public static void ensureValidSession() throws Exception { ensureValidSession(requireSession()); }
    public static void ensureValidSession(SessionLease lease) throws Exception {
        synchronized(STATE_LOCK) { check(lease); if(fresh(state))return; }
        // Serializes one-use refresh tokens, while clearSession never needs this lock.
        synchronized(REFRESH_LOCK) {
            State before;
            synchronized(STATE_LOCK) { check(lease);before=state;if(fresh(before))return; }
            if(before.refreshToken()==null){clearIfCurrent(lease.epoch());throw changed();}
            try {
                JSONObject response=auth("/auth/v1/token?grant_type=refresh_token",new JSONObject().put("refresh_token",before.refreshToken()));
                if(!response.has("user") || !lease.userId().equals(response.getJSONObject("user").getString("id")))throw changed();
                if(!acceptSession(lease.epoch(),response,before.email(),true))throw changed();
                loadProfile(lease);
                synchronized(STATE_LOCK){check(lease);if(state.blocked())throw changed();}
            } catch(Exception ex) {clearIfCurrent(lease.epoch());throw ex;}
        }
    }
    private static boolean fresh(State current) { return current.accessToken()!=null && Instant.now().isBefore(current.expiresAt().minusSeconds(30)); }
    private static JSONObject auth(String path,JSONObject payload) throws Exception {
        HttpRequest request=HttpRequest.newBuilder(URI.create(Config.SUPABASE_URL+path)).header("apikey",Config.SUPABASE_ANON_KEY)
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(payload.toString())).build();
        return new JSONObject(SupabaseHttp.jsonBody(SupabaseHttp.send(request)));
    }
    private static boolean acceptSession(long epoch,JSONObject response,String email,boolean preserveRoles) {
        if(!response.has("access_token")||!response.has("user"))return false;
        JSONObject user=response.getJSONObject("user");
        String token=response.getString("access_token"),id=user.getString("id");
        if(token.isBlank()||id.isBlank())return false;
        synchronized(STATE_LOCK) {
            if(state.epoch()!=epoch)return false;
            if(preserveRoles&&!Objects.equals(state.userId(),id))return false;
            state=new State(epoch,token,response.optString("refresh_token",null),user.optString("email",email),id,
                    preserveRoles&&state.admin(),preserveRoles&&state.manager(),preserveRoles&&state.blocked(),false,
                    Instant.now().plusSeconds(Math.max(0,response.optLong("expires_in",3600))));
            return true;
        }
    }
    private static void loadProfile(SessionLease lease) throws Exception {
        HttpRequest request=HttpRequest.newBuilder(URI.create(Config.SUPABASE_URL+"/rest/v1/profiles?id=eq."+SupabaseHttp.queryValue(lease.userId())+"&select=is_admin,is_manager,is_blocked,blocked"))
                .header("apikey",Config.SUPABASE_ANON_KEY).GET().build();
        JSONArray profiles=new JSONArray(SupabaseHttp.jsonBody(SupabaseHttp.sendWithCurrentToken(request,lease)));
        boolean admin=false,manager=false,blocked=false;
        if(!profiles.isEmpty()) {
            JSONObject profile=profiles.getJSONObject(0);admin=profile.optBoolean("is_admin",false);manager=profile.optBoolean("is_manager",false);
            blocked=profile.optBoolean("is_blocked",false)||profile.optBoolean("blocked",false);
        } else {
            String email;
            synchronized(STATE_LOCK){check(lease);email=state.email();}
            JSONObject profile=new JSONObject().put("id",lease.userId()).put("email",email).put("name","").put("surname","").put("phone","").put("city","").put("address","");
            HttpRequest create=HttpRequest.newBuilder(URI.create(Config.SUPABASE_URL+"/rest/v1/profiles?on_conflict=id"))
                    .header("apikey",Config.SUPABASE_ANON_KEY).header("Content-Type","application/json").header("Prefer","resolution=ignore-duplicates,return=minimal")
                    .POST(HttpRequest.BodyPublishers.ofString(profile.toString())).build();
            SupabaseHttp.sendWithCurrentToken(create,lease);
        }
        synchronized(STATE_LOCK) {
            check(lease);State s=state;
            state=new State(s.epoch(),s.accessToken(),s.refreshToken(),s.email(),s.userId(),admin,manager,blocked,s.confirmation(),s.expiresAt());
        }
    }
}

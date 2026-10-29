package com.example.authapp.network;

import com.sun.net.httpserver.HttpServer;
import config.Config;
import config.SessionManager;
import org.json.JSONObject;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Runs only in an isolated JVM against its own fake loopback API. */
public final class PublishableTransportProbe {
    private static final String A="11111111-1111-1111-1111-111111111111",B="22222222-2222-2222-2222-222222222222";
    private record Seen(String path,String grant,List<String> apiKeys,String authorization){}
    private static final List<Seen> requests=new CopyOnWriteArrayList<>();
    private static final AtomicBoolean shortExpiry=new AtomicBoolean();
    private static final AtomicInteger refreshes=new AtomicInteger();
    public static void main(String[] args)throws Exception{
        boolean modern=args.length==0||args[0].equals("modern");
        String key=modern?"sb_publishable_loopback_test":"eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiYW5vbiJ9.test";
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{
            String path=exchange.getRequestURI().getPath(),query=exchange.getRequestURI().getRawQuery();
            requests.add(new Seen(path,query,exchange.getRequestHeaders().getOrDefault("apikey",List.of()),exchange.getRequestHeaders().getFirst("Authorization")));
            String response="[]";
            try{
                if(path.equals("/auth/v1/token")){
                    JSONObject payload=new JSONObject(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
                    boolean refresh=query!=null&&query.contains("refresh_token");
                    String user=refresh?"A":payload.getString("email").startsWith("a@")?"A":"B";
                    String token="access-"+user;
                    if(refresh){refreshes.incrementAndGet();token+="-refreshed";}
                    response=new JSONObject().put("access_token",token).put("refresh_token","refresh-"+user).put("expires_in",shortExpiry.get()?1:3600)
                            .put("user",new JSONObject().put("id",user.equals("A")?A:B).put("email",user.toLowerCase()+"@example.invalid")).toString();
                }else if(path.equals("/rest/v1/profiles"))response="[{\"is_admin\":false,\"is_manager\":false,\"is_blocked\":false,\"blocked\":false}]";
                else if(path.equals("/auth/v1/user"))response="{}";
                byte[] bytes=response.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);
            }finally{exchange.close();}
        });
        server.start();
        System.setProperty("techstore.supabase.url","http://127.0.0.1:"+server.getAddress().getPort());
        System.setProperty("techstore.supabase.key",key);
        try{
            String guest=modern?null:"Bearer "+key;
            check(Config.SUPABASE_ANON_KEY.equals(key),"probe Config key");
            HttpRequest publicBeforeLogin=SupabaseHttp.request("/rest/v1/public-before-login").GET().build();
            check(publicBeforeLogin.headers().firstValue("Authorization").orElse(null)==null||!modern,"builder sent publishable as bearer");
            SupabaseHttp.send(SupabaseHttp.request("/rest/v1/products").GET().build());assertLast("/rest/v1/products",key,guest);
            // Old repositories still attach their public key as a bearer. The
            // transport must normalize it and replace rather than duplicate apikey.
            SupabaseHttp.send(HttpRequest.newBuilder(URI.create(Config.SUPABASE_URL+"/storage/v1/object/public/test"))
                    .header("apikey","obsolete-placeholder").header("Authorization","Bearer "+key).GET().build());
            assertLast("/storage/v1/object/public/test",key,guest);
            SupabaseHttp.send(SupabaseHttp.request("/auth/v1/settings").GET().build());assertLast("/auth/v1/settings",key,guest);
            SupabaseHttp.send(HttpRequest.newBuilder(URI.create(Config.SUPABASE_URL+"/auth/v1/settings"))
                    .header("Authorization","Bearer "+key).GET().build());assertLast("/auth/v1/settings",key,guest);
            check(SessionManager.login("a@example.invalid","test-password"),"login A");
            check(requests.stream().filter(s->s.path.equals("/auth/v1/token")).allMatch(s->s.authorization==null),"password login must be API-key-only");
            assertLast("/rest/v1/profiles",key,"Bearer access-A");
            SupabaseHttp.send(publicBeforeLogin);assertLast("/rest/v1/public-before-login",key,guest);
            var lease=SessionManager.requireSession();
            try(var scope=SupabaseHttp.forSession(lease)){
                SupabaseHttp.send(SupabaseHttp.request("/rest/v1/cart_items").GET().build());assertLast("/rest/v1/cart_items",key,"Bearer access-A");
                SupabaseHttp.send(SupabaseHttp.request("/storage/v1/object/test").DELETE().build());assertLast("/storage/v1/object/test",key,"Bearer access-A");
                SupabaseHttp.send(SupabaseHttp.request("/auth/v1/user").header("Content-Type","application/json").PUT(HttpRequest.BodyPublishers.ofString("{}" )).build());
                assertLast("/auth/v1/user",key,"Bearer access-A");
            }
            SessionManager.clearSession();shortExpiry.set(true);check(SessionManager.login("a@example.invalid","test-password"),"short-lived A login");shortExpiry.set(false);
            lease=SessionManager.requireSession();
            try(var scope=SupabaseHttp.forSession(lease)){
                SupabaseHttp.send(SupabaseHttp.request("/rest/v1/refresh-check").GET().build());
                assertLast("/rest/v1/refresh-check",key,"Bearer access-A-refreshed");
            }
            check(refreshes.get()==1,"refresh should happen once without recursion");
            Seen refresh=requests.stream().filter(s->s.path.equals("/auth/v1/token")&&s.grant.contains("refresh_token")).findFirst().orElseThrow();
            check(refresh.authorization==null,"expired session JWT was attached to refresh endpoint");
            HttpRequest stale=SupabaseHttp.request("/rest/v1/stale").GET().build();
            SessionManager.clearSession();check(SessionManager.login("b@example.invalid","test-password"),"login B");int before=requests.size();
            boolean rejected=false;try{SupabaseHttp.send(stale);}catch(java.io.IOException expected){rejected=true;}
            check(rejected&&requests.size()==before,"stale A bearer reached network or upgraded to B");
            check(requests.stream().allMatch(s->s.apiKeys.equals(List.of(key))),"missing, wrong or duplicate apikey");
            System.out.println("PASS: "+(modern?"modern publishable":"legacy anon")+" guest/authenticated REST/Storage/Auth, raw header normalization, pinned session, refresh and stale bearer.");
        }finally{SessionManager.clearSession();server.stop(0);}
    }
    private static void assertLast(String path,String key,String bearer){
        Seen seen=requests.getLast();check(seen.path.equals(path),"wrong request path");check(seen.apiKeys.equals(List.of(key)),"wrong apikey headers");check(java.util.Objects.equals(seen.authorization,bearer),"wrong Authorization for "+path);
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}

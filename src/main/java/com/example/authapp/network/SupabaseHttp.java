package com.example.authapp.network;

import config.Config;
import config.SessionManager;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Bounded transport. An operation can pin its session across several requests. */
public final class SupabaseHttp {
    private static final HttpClient CLIENT=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    private static final ThreadLocal<SessionManager.SessionLease> SESSION_SCOPE=new ThreadLocal<>();
    private SupabaseHttp() {}
    public static final class SessionScope implements AutoCloseable {
        private final SessionManager.SessionLease previous;
        private final Thread owner=Thread.currentThread();
        private boolean closed;
        private SessionScope(SessionManager.SessionLease lease){previous=SESSION_SCOPE.get();if(previous!=null)SessionManager.assertCurrent(previous);SessionManager.assertCurrent(lease);SESSION_SCOPE.set(lease);}
        @Override public void close(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Session scope belongs to its worker");if(!closed){closed=true;if(previous==null)SESSION_SCOPE.remove();else SESSION_SCOPE.set(previous);}}
    }
    public static SessionScope forSession(SessionManager.SessionLease lease){return new SessionScope(lease);}
    public static String authorization(){
        SessionManager.SessionLease scoped=SESSION_SCOPE.get();
        if(scoped!=null)return SessionManager.tokenFor(scoped);
        String token=SessionManager.getAccessToken();return token==null||token.isBlank()?Config.SUPABASE_ANON_KEY:token;
    }
    public static String requireToken(){SessionManager.SessionLease lease=SESSION_SCOPE.get();return SessionManager.tokenFor(lease==null?SessionManager.requireSession():lease);}
    public static String queryValue(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    public static HttpRequest.Builder request(String path){
        HttpRequest.Builder builder=HttpRequest.newBuilder(URI.create(Config.SUPABASE_URL+path)).timeout(Duration.ofSeconds(30))
                .header("apikey",Config.SUPABASE_ANON_KEY);
        String token=authorization();
        // Publishable project keys identify an application; they are not user JWTs.
        if(!isPublishable(token))builder.header("Authorization","Bearer "+token);
        return builder;
    }
    private static boolean isPublishable(String key){return key.startsWith("sb_publishable_");}
    private static String bearer(HttpRequest request){
        String value=request.headers().firstValue("Authorization").orElse("");
        return value.regionMatches(true,0,"Bearer ",0,7)?value.substring(7):value;
    }
    private static String guestBearer(){return isPublishable(Config.SUPABASE_ANON_KEY)?null:Config.SUPABASE_ANON_KEY;}
    public static HttpResponse<String> send(HttpRequest original) throws IOException,InterruptedException {return sendInternal(original,null,true);}
    /** Used after Auth already supplied a fresh token, avoiding recursive profile refresh. */
    public static HttpResponse<String> sendWithCurrentToken(HttpRequest original,SessionManager.SessionLease lease) throws IOException,InterruptedException{return sendInternal(original,lease,false);}
    private static HttpResponse<String> sendInternal(HttpRequest original,SessionManager.SessionLease explicit,boolean allowRefresh) throws IOException,InterruptedException {
        boolean data=original.uri().getPath().startsWith("/rest/v1/")||original.uri().getPath().startsWith("/storage/v1/");
        boolean auth=original.uri().getPath().startsWith("/auth/v1/");
        boolean api=data||auth;
        SessionManager.SessionLease lease=explicit!=null?explicit:SESSION_SCOPE.get();
        String incomingToken=bearer(original);
        try {
            if(api&&lease==null){
                // Never turn a public request into a newly signed-in user's request.
                if(!incomingToken.isBlank()&&!incomingToken.equals(Config.SUPABASE_ANON_KEY))lease=SessionManager.captureForToken(incomingToken);
            }
            if(lease!=null){SessionManager.assertCurrent(lease);if(data&&allowRefresh)SessionManager.ensureValidSession(lease);}
            HttpRequest.Builder builder=HttpRequest.newBuilder(original,(name,value)->!api||(!name.equalsIgnoreCase("Authorization")&&!name.equalsIgnoreCase("apikey")))
                    .timeout(original.timeout().orElse(Duration.ofSeconds(30)));
            if(api){
                builder.header("apikey",Config.SUPABASE_ANON_KEY);
                String token=null;
                if(data)token=lease==null?guestBearer():SessionManager.tokenFor(lease);
                else if(!incomingToken.isBlank())token=incomingToken.equals(Config.SUPABASE_ANON_KEY)?guestBearer():SessionManager.tokenFor(lease);
                // Auth login/refresh intentionally remain API-key-only when their
                // original request has no bearer, even inside a pinned operation.
                // Sending an expired access token here would break token refresh.
                if(token!=null)builder.header("Authorization","Bearer "+token);
            }
            if(lease!=null)SessionManager.assertCurrent(lease);
            HttpResponse<String> response=CLIENT.send(builder.build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if(lease!=null)SessionManager.assertCurrent(lease);
            checkStatus(response.statusCode());return response;
        } catch(InterruptedException ex){Thread.currentThread().interrupt();throw ex;}
        catch(IOException ex){throw ex;}
        catch(Exception ex){throw new IOException("Сессия изменилась или недоступна. Войдите снова и повторите действие.",ex);}
    }
    public static void checkStatus(int status) throws IOException {
        if(status>=200&&status<300)return;
        String message=switch(status){case 401->"Сессия истекла. Войдите снова";case 403->"Недостаточно прав для операции";
            case 404->"Серверная операция ещё не настроена или ресурс не найден";case 409->"Данные изменились. Обновите экран и повторите операцию";
            case 429->"Слишком много запросов. Повторите позже";default->status>=500?"Сервис временно недоступен":"Запрос отклонён сервером";};
        throw new IOException(message+" (HTTP "+status+")");
    }
    public static String jsonBody(HttpResponse<String> response) throws IOException {
        if(response.body()==null||response.body().isBlank())throw new IOException("Сервер вернул пустой ответ");return response.body();
    }
}

package com.example.authapp.repositories;

import com.example.authapp.models.Product;
import config.SessionManager;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONObject;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** This probe creates its fake API before any production Config is initialized. */
public final class RepositoryRlsContractProbe {
    private static final String A="11111111-1111-1111-1111-111111111111",B="22222222-2222-2222-2222-222222222222";
    private static final List<Throwable> serverFailures=new CopyOnWriteArrayList<>();
    private static final AtomicInteger profileRequests=new AtomicInteger(),productRequests=new AtomicInteger();
    private static final AtomicBoolean delayProfile=new AtomicBoolean();
    private static final CountDownLatch profileArrived=new CountDownLatch(1),releaseProfile=new CountDownLatch(1);
    public static void main(String[] args) throws Exception {
        ExecutorService serverThreads=Executors.newCachedThreadPool(r->{Thread t=new Thread(r,"fake-repo-api");t.setDaemon(true);return t;});
        ExecutorService worker=Executors.newSingleThreadExecutor();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setExecutor(serverThreads);
        server.createContext("/",exchange->{
            try {
                String path=exchange.getRequestURI().getPath(),query=exchange.getRequestURI().getRawQuery(),method=exchange.getRequestMethod();
                String body=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8),result="{}";int status=200;
                if(path.equals("/auth/v1/token")){
                    String email=new JSONObject(body).getString("email");boolean second=email.startsWith("b@");
                    result=new JSONObject().put("access_token",second?"test-B":"test-A").put("refresh_token","test-refresh").put("expires_in",3600)
                            .put("user",new JSONObject().put("id",second?B:A).put("email",email)).toString();
                } else if(path.equals("/rest/v1/products")){
                    productRequests.incrementAndGet();JSONObject payload=new JSONObject(body);
                    check(!payload.has("id"),"REST product payload sent forbidden id column");
                    check(payload.keySet().equals(Set.of("name","description","price","stock","image_url","category","manufacturer")),"product mutable column set changed");
                    if(method.equals("POST"))status=201;
                    else {check(method.equals("PATCH")&&"id=eq.17".equals(query),"product PATCH target incorrect");status=204;}
                } else if(path.equals("/rest/v1/profiles")){
                    profileRequests.incrementAndGet();boolean second=exchange.getRequestHeaders().getFirst("Authorization").equals("Bearer test-B");
                    String user=second?B:A,email=second?"b@example.invalid":"a@example.invalid";
                    if(method.equals("GET")){
                        check(query!=null&&query.startsWith("id=eq."+user+"&select="),"profile read must use the session id");
                        check(!query.contains("email=eq."),"profile read still filters by mutable email");
                        if(delayProfile.get()&&query.endsWith("select=*")&&!second){profileArrived.countDown();check(releaseProfile.await(6,TimeUnit.SECONDS),"profile gate timeout");}
                    } else if(method.equals("PATCH")){
                        check(("id=eq."+user).equals(query),"profile PATCH must use the session id");
                        check(new JSONObject(body).keySet().equals(Set.of("name","surname","phone","city","address")),"profile PATCH sent privileged columns");status=204;
                    } else if(method.equals("POST")){
                        JSONObject payload=new JSONObject(body);check(payload.keySet().equals(Set.of("id","email","name","surname","phone","city","address")),"profile create sent privileged columns");
                        check(user.equals(payload.getString("id"))&&email.equals(payload.getString("email")),"profile create identity mismatch");status=201;
                    }
                    result="["+new JSONObject().put("id",user).put("email",email).put("name","Test").put("is_admin",false).put("is_manager",false).put("is_blocked",false).put("blocked",false)+"]";
                } else throw new AssertionError("unexpected fake API path");
                exchange.getResponseHeaders().set("Content-Type","application/json");
                if(status==204)exchange.sendResponseHeaders(status,-1);
                else {byte[] bytes=result.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);}
            } catch(Throwable ex){serverFailures.add(ex);try{exchange.sendResponseHeaders(500,-1);}catch(Exception ignored){}}
            finally{exchange.close();}
        });
        server.start();System.setProperty("techstore.supabase.url","http://127.0.0.1:"+server.getAddress().getPort());System.setProperty("techstore.supabase.key","sb_publishable_test");
        try {
            check(SessionManager.login("a@example.invalid","test"),"fixture login failed");
            Product product=new Product(17,"Test product","description",1000,5,"https://example.invalid/photo.png","Процессоры","Test");
            ProductRepository.addProductToSupabase(product);ProductRepository.updateProductInSupabase(product);
            check(productRequests.get()==2,"product create/PATCH were not exercised");
            check(UserRepository.getUserProfileByEmail("a@example.invalid").id.equals(A),"own profile read by email failed");
            check(UserRepository.getUserProfile(A).id.equals(A),"own profile read by id failed");
            UserRepository.updateUserProfile("a@example.invalid","Test","Name","","City","Address");
            UserRepository.createUserProfile(A,"a@example.invalid","Test","Name");
            int before=profileRequests.get();
            expectFailure(()->UserRepository.getUserProfileByEmail("b@example.invalid"));
            expectFailure(()->UserRepository.getUserProfile(B));
            expectFailure(()->UserRepository.updateUserProfile("b@example.invalid","X","Y","","",""));
            expectFailure(()->UserRepository.createUserProfile(B,"a@example.invalid","X","Y"));
            check(profileRequests.get()==before,"wrong profile identity reached the API");
            delayProfile.set(true);
            Future<?> oldRequest=worker.submit(()->{try{UserRepository.getUserProfile(A);throw new AssertionError("late A profile response accepted under B");}catch(Exception expected){}});
            check(profileArrived.await(4,TimeUnit.SECONDS),"delayed profile request never started");
            SessionManager.clearSession();check(SessionManager.login("b@example.invalid","test"),"fixture B login failed");
            releaseProfile.countDown();oldRequest.get(5,TimeUnit.SECONDS);
            check(SessionManager.getUserId().equals(B),"late profile response changed B session");
            SessionManager.clearSession();int productBefore=productRequests.get();expectFailure(()->ProductRepository.updateProductInSupabase(product));
            check(productRequests.get()==productBefore,"signed-out product write reached the API");
            check(serverFailures.isEmpty(),"fake server assertions failed: "+serverFailures);
            System.out.println("PASS: restricted product columns/201/204, own profile id filtering, role fields excluded, wrong identity and late responses rejected.");
        } finally {releaseProfile.countDown();server.stop(0);worker.shutdownNow();serverThreads.shutdownNow();SessionManager.clearSession();}
    }
    private interface Checked {void run() throws Exception;}
    private static void expectFailure(Checked action)throws Exception {boolean failed=false;try{action.run();}catch(Exception expected){failed=true;}check(failed,"expected repository request rejection");}
    private static void check(boolean result,String message){if(!result)throw new AssertionError(message);}
}

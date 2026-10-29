package config;

import com.example.authapp.models.Cart;
import com.example.authapp.models.Product;
import com.example.authapp.network.SupabaseHttp;
import com.example.authapp.services.CartService;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONObject;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Separate JVM prevents static client configuration from contacting any real service. */
public final class SessionRaceProbe {
    private static final String A="11111111-1111-1111-1111-111111111111",B="22222222-2222-2222-2222-222222222222";
    private static volatile Gate delayedWrite,delayedLogin,delayedRefresh;
    private static volatile boolean shortExpiry;
    private static final List<String> cartHeaders=new CopyOnWriteArrayList<>();
    private static final AtomicInteger cartRequests=new AtomicInteger();
    private static final class Gate {
        final CountDownLatch arrived=new CountDownLatch(1),release=new CountDownLatch(1);
        void block() throws InterruptedException {arrived.countDown();check(release.await(8,TimeUnit.SECONDS),"gate timeout");}
        void await() throws InterruptedException {check(arrived.await(4,TimeUnit.SECONDS),"request never reached fake server");}
        void open(){release.countDown();}
    }
    public static void main(String[] args) throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        ExecutorService requests=Executors.newCachedThreadPool(r->{Thread t=new Thread(r,"fake-auth");t.setDaemon(true);return t;});
        ExecutorService work=Executors.newCachedThreadPool();server.setExecutor(requests);
        server.createContext("/",exchange->{
            try {
                String path=exchange.getRequestURI().getPath(),query=exchange.getRequestURI().getRawQuery();
                String body=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8),response="[]";int status=200;
                if(path.equals("/auth/v1/token")){
                    JSONObject payload=new JSONObject(body);boolean refresh=query!=null&&query.contains("refresh_token");
                    String who=refresh?payload.getString("refresh_token").substring("refresh-".length()):payload.getString("email").startsWith("a@")?"A":"B";
                    Gate gate=refresh?delayedRefresh:who.equals("A")?delayedLogin:null;if(gate!=null)gate.block();
                    response=new JSONObject().put("access_token","access-"+who).put("refresh_token","refresh-"+who)
                            .put("expires_in",shortExpiry?1:3600).put("user",new JSONObject().put("id",who.equals("A")?A:B).put("email",who.equals("A")?"a@example.invalid":"b@example.invalid")).toString();
                }else if(path.equals("/rest/v1/profiles"))response="[{\"is_admin\":false,\"is_manager\":false,\"is_blocked\":false,\"blocked\":false}]";
                else if(path.equals("/rest/v1/cart_items")){
                    cartRequests.incrementAndGet();cartHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
                    if(exchange.getRequestMethod().equals("POST")){Gate gate=delayedWrite;if(gate!=null)gate.block();status=201;response="{}";}
                }
                byte[] bytes=response.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);
            }catch(Exception ex){try{exchange.sendResponseHeaders(500,-1);}catch(Exception ignored){}}
            finally{exchange.close();}
        });
        server.start();System.setProperty("techstore.supabase.url","http://127.0.0.1:"+server.getAddress().getPort());System.setProperty("techstore.supabase.key","sb_publishable_test");
        try {
            check(SessionManager.login("a@example.invalid","test"),"login A");
            delayedWrite=new Gate();Gate write=delayedWrite;
            Future<?> oldCart=work.submit(()->{try{new CartService().addProductToCart(product(11),1);throw new AssertionError("stale cart response accepted");}catch(Exception expected){}});
            write.await();quickLogout();check(SessionManager.login("b@example.invalid","test"),"login B while A request pending");Cart.getInstance().addProduct(product(22),2);
            write.open();oldCart.get(5,TimeUnit.SECONDS);delayedWrite=null;
            check(Cart.getInstance().getItems().size()==1&&Cart.getInstance().getItems().getFirst().getProduct().getId()==22&&Cart.getInstance().getTotalQuantity()==2,"old request polluted B cart");
            check(cartHeaders.stream().allMatch("Bearer access-A"::equals),"old account operation used B token");
            check(B.equals(SessionManager.getUserId()),"cart failure cleared B session");

            check(SessionManager.login("a@example.invalid","test"),"login A again");
            var oldLease=SessionManager.requireSession();HttpRequest oldRequest=SupabaseHttp.request("/rest/v1/cart_items?user_id=eq."+A).GET().build();
            SessionManager.clearSession();check(SessionManager.login("b@example.invalid","test"),"login B again");int before=cartRequests.get();
            expectFailure(()->SupabaseHttp.send(oldRequest));check(cartRequests.get()==before,"stale bearer request reached server");
            expectFailure(()->SessionManager.runIfCurrent(oldLease,()->Cart.getInstance().addProduct(product(99),1)));
            expectFailure(()->{try(var scope=SupabaseHttp.forSession(oldLease)){throw new AssertionError("stale scope allowed");}});

            delayedLogin=new Gate();Gate login=delayedLogin;Future<Boolean> lateLogin=work.submit(()->SessionManager.login("a@example.invalid","test"));
            login.await();quickLogout();login.open();check(!lateLogin.get(5,TimeUnit.SECONDS),"late login restored session after logout");delayedLogin=null;
            check(SessionManager.getAccessToken()==null&&SessionManager.getUserId()==null,"late login resurrected credentials");

            shortExpiry=true;check(SessionManager.login("a@example.invalid","test"),"short-lived login");shortExpiry=false;
            delayedRefresh=new Gate();Gate refresh=delayedRefresh;
            Future<?> lateRefresh=work.submit(()->{try{SessionManager.ensureValidSession();throw new AssertionError("late refresh accepted");}catch(Exception expected){}});
            refresh.await();quickLogout();check(SessionManager.login("b@example.invalid","test"),"login B while A refresh pending");
            refresh.open();lateRefresh.get(5,TimeUnit.SECONDS);delayedRefresh=null;
            check(B.equals(SessionManager.getUserId())&&"access-B".equals(SessionManager.getAccessToken()),"old refresh replaced/cleared B session");

            check(SessionManager.login("a@example.invalid","test"),"same user epoch before");var sameUserOldLease=SessionManager.requireSession();
            SessionManager.clearSession();check(SessionManager.login("a@example.invalid","test"),"same user epoch after");expectFailure(()->SessionManager.assertCurrent(sameUserOldLease));

            SessionManager.clearSession();Product p=product(31);Cart.getInstance().addProduct(p,2);var snapshot=Cart.getInstance().getItems();
            p.setPrice(90000);snapshot.getFirst().getProduct().setPrice(99999);snapshot.getFirst().setQuantity(999);
            check(Cart.getInstance().getTotal()==2000&&Cart.getInstance().getTotalQuantity()==2,"cart snapshot shared mutable item/product");
            expectFailure(snapshot::clear);Cart.getInstance().addProduct(product(32),1);check(snapshot.size()==1,"snapshot is a live list");
            System.out.println("PASS: delayed cart, stale bearer/scope, delayed login/refresh, same-user epochs and detached cart snapshots.");
        } finally {
            for(Gate gate:new Gate[]{delayedWrite,delayedLogin,delayedRefresh})if(gate!=null)gate.open();
            work.shutdownNow();requests.shutdownNow();server.stop(0);SessionManager.clearSession();
        }
    }
    private static Product product(int id){return new Product(id,"Test "+id,"",1000,10,"","Процессоры","");}
    private static void quickLogout(){long start=System.nanoTime();SessionManager.clearSession();check(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)<250,"logout blocked on network monitor");}
    private interface Checked {void run() throws Exception;}
    private static void expectFailure(Checked action)throws Exception {boolean failed=false;try{action.run();}catch(Exception expected){failed=true;}check(failed,"expected operation rejection");}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}

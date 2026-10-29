package com.example.authapp.network;

import com.example.authapp.models.Cart;
import com.google.gson.Gson;
import config.SessionManager;
import javafx.beans.property.SimpleStringProperty;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Both key modes use fresh JVMs; tests can never reach the configured real service. */
class PublishableTransportTest {
    @Test void modernKeyUsesApikeyAndOnlyUserJwtInBearer()throws Exception{probe("modern");}
    @Test void legacyAnonAndRefreshRemainCompatible()throws Exception{probe("legacy");}
    private void probe(String mode)throws Exception{
        Set<String> entries=new LinkedHashSet<>();
        for(Class<?> type:new Class<?>[]{PublishableTransportProbe.class,SessionManager.class,Cart.class,Gson.class,JSONObject.class,SimpleStringProperty.class})
            entries.add(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
        Process process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"--add-modules=jdk.httpserver","-cp",String.join(java.io.File.pathSeparator,entries),PublishableTransportProbe.class.getName(),mode).redirectErrorStream(true).start();
        try{
            assertTrue(process.waitFor(25,TimeUnit.SECONDS),"transport probe timed out");
            String output=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
            assertEquals(0,process.exitValue(),output);assertTrue(output.contains("PASS:"),output);
        }finally{if(process.isAlive())process.destroyForcibly();}
    }
}

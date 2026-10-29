package com.example.authapp.repositories;

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

/** A fresh JVM ensures static Config can only point at this test's loopback API. */
class RepositoryRlsContractTest {
    @Test void restrictedColumnContractsAndOwnProfileScopeMatchRls() throws Exception {
        Set<String> entries=new LinkedHashSet<>();
        for(Class<?> type:new Class<?>[]{RepositoryRlsContractProbe.class,SessionManager.class,Cart.class,Gson.class,JSONObject.class,SimpleStringProperty.class})
            entries.add(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
        Process process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),
                "--add-modules=jdk.httpserver","-cp",String.join(java.io.File.pathSeparator,entries),RepositoryRlsContractProbe.class.getName())
                .redirectErrorStream(true).start();
        try {
            assertTrue(process.waitFor(25,TimeUnit.SECONDS),"repository contract test timed out");
            String output=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
            assertEquals(0,process.exitValue(),output);assertTrue(output.contains("PASS:"),output);
        } finally {if(process.isAlive())process.destroyForcibly();}
    }
}

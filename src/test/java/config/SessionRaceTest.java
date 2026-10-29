package config;

import com.example.authapp.models.Cart;
import com.google.gson.Gson;
import javafx.beans.property.SimpleStringProperty;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Uses a fresh Config and fake loopback server, regardless of test class execution order. */
class SessionRaceTest {
    @Test void lateOperationsNeverCrossAccountBoundaryAndLogoutDoesNotWaitForNetwork() throws Exception {
        Set<String> entries=new LinkedHashSet<>();
        for(Class<?> type:new Class<?>[]{SessionRaceProbe.class,SessionManager.class,Cart.class,Gson.class,JSONObject.class,SimpleStringProperty.class})
            entries.add(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
        String javaExecutable=Path.of(System.getProperty("java.home"),"bin","java").toString();
        Process process=new ProcessBuilder(javaExecutable,"--add-modules=jdk.httpserver","-cp",String.join(java.io.File.pathSeparator,entries),SessionRaceProbe.class.getName()).redirectErrorStream(true).start();
        try {
            assertTrue(process.waitFor(25,TimeUnit.SECONDS),"isolated session regression timed out");
            String output=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
            assertEquals(0,process.exitValue(),output);assertTrue(output.contains("PASS:"),output);
        } finally {if(process.isAlive())process.destroyForcibly();}
    }
}

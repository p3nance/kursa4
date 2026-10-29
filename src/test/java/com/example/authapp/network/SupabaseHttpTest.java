package com.example.authapp.network;

import config.Config;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class SupabaseHttpTest {
    @Test void mapsUnauthorizedWithoutLeakingResponse() {
        IOException error = assertThrows(IOException.class, () -> SupabaseHttp.checkStatus(401));
        assertTrue(error.getMessage().contains("Войдите снова"));
        assertDoesNotThrow(() -> SupabaseHttp.checkStatus(204));
        assertThrows(IOException.class, () -> SupabaseHttp.checkStatus(302));
    }
    @Test void rejectsPrivilegedKeyEvenIfSuppliedByEnvironment() {
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"role\":\"service_role\"}".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> Config.validatePublicKey("header." + payload + ".signature"));
        assertThrows(IllegalArgumentException.class, () -> Config.validatePublicKey("sb_secret_example"));
        assertThrows(IllegalArgumentException.class, () -> Config.validatePublicKey(""));
        assertThrows(IllegalArgumentException.class, () -> Config.validatePublicKey(null));
        assertEquals("sb_publishable_example", Config.validatePublicKey("sb_publishable_example"));
    }
    @Test void encodesFiltersWithoutChangingTheirMeaning() {
        assertEquals("a%2Bb%40example.com", SupabaseHttp.queryValue("a+b@example.com"));
    }
}

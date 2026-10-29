package config;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Public client configuration. Privileged credentials belong on a server. */
public final class Config {
    private Config() {}
    private static final java.util.Properties LOCAL = loadLocalSettings();

    private static java.util.Properties loadLocalSettings() {
        java.util.Properties settings = new java.util.Properties();
        java.nio.file.Path path = java.nio.file.Path.of("config.local.properties");
        if (java.nio.file.Files.exists(path)) {
            try (java.io.Reader reader = java.nio.file.Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                settings.load(reader);
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Не удалось прочитать config.local.properties", e);
            }
        }
        return settings;
    }
    public static final String SUPABASE_URL = setting("techstore.supabase.url", "SUPABASE_URL", "https://qsthuhzkciimucarscco.supabase.co").replaceAll("/+$", "");
    public static final String SUPABASE_ANON_KEY = validatePublicKey(setting("techstore.supabase.key", "SUPABASE_PUBLISHABLE_KEY", ""));

    private static String setting(String property, String environment, String fallback) {
        String value = System.getProperty(property, System.getenv(environment));
        if (value == null || value.isBlank()) value = LOCAL.getProperty(environment);
        return value == null || value.isBlank() ? fallback : value.trim();
    }
    public static String validatePublicKey(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Укажите SUPABASE_PUBLISHABLE_KEY через переменную окружения, -Dtechstore.supabase.key или config.local.properties");
        if (key.startsWith("sb_secret_")) throw new IllegalArgumentException("Секретный ключ Supabase запрещён в клиенте");
        if (key.startsWith("sb_publishable_")) return key;
        try {
            String[] parts = key.split("\\.");
            String role = JsonParser.parseString(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8))
                    .getAsJsonObject().get("role").getAsString();
            if (!"anon".equals(role)) throw new IllegalArgumentException("В клиенте разрешён только публичный ключ Supabase");
            return key;
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("Некорректный публичный ключ Supabase"); }
    }
    public static void validateConfig() {
        java.net.URI uri = java.net.URI.create(SUPABASE_URL);
        if (!"https".equals(uri.getScheme()) && !("http".equals(uri.getScheme()) && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost())))) {
            throw new IllegalArgumentException("Supabase URL должен использовать HTTPS (HTTP разрешён только локально)");
        }
    }
    public static String getConfigInfo() { return "Supabase URL: " + SUPABASE_URL; }
}

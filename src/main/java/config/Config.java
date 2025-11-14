package config;

public class Config {
    public static final String SUPABASE_URL = "https://qsthuhzkciimucarscco.supabase.co";
    public static final String SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InFzdGh1aHprY2lpbXVjYXJzY2NvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTc0MjM4MTUsImV4cCI6MjA3Mjk5OTgxNX0.VnHrSq-S8NlSmzQ7_soRvrc7t3s3fEp_wu9tTwm9ZUI";

    public static void validateConfig() {
        if (SUPABASE_URL.contains("your-project") || SUPABASE_ANON_KEY.contains("your-key")) {
            System.err.println("⚠️ ATTENTION: Supabase settings are not configured!");
            System.err.println("1. Go to Supabase Dashboard");
            System.err.println("2. Create or select a project");
            System.err.println("3. In Settings → API find URL and anon/public key");
            System.err.println("4. In Authentication → Settings enable Email provider");
            System.err.println("5. Replace values in config.Config.java");
        }
    }
}

package config;

public class Config {
    // ВАЖНО заменить на ваши значения из Supabase Dashboard с https://
    public static final String SUPABASE_URL = "https://qsthuhzkciimucarscco.supabase.co";
    public static final String SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InFzdGh1aHprY2lpbXVjYXJzY2NvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTc0MjM4MTUsImV4cCI6MjA3Mjk5OTgxNX0.VnHrSq-S8NlSmzQ7_soRvrc7t3s3fEp_wu9tTwm9ZUI";

    public static void validateConfig() {
        if (SUPABASE_URL.contains("your-project") || SUPABASE_ANON_KEY.contains("your-key")) {
            System.err.println("⚠️ ВНИМАНИЕ: Настройки Supabase не настроены!");
            System.err.println("1. Зайдите в Supabase Dashboard");
            System.err.println("2. Создайте проект или выберите существующий");
            System.err.println("3. В Settings → API найдите URL и anon/public key");
            System.err.println("4. В Authentication → Settings включите Email provider");
            System.err.println("5. Замените значения в config.Config.java");
        } else {
        }
    }

    public static String getConfigInfo() {
        return String.format(
                "Supabase Config:\nURL: %s\nKey: %s",
                SUPABASE_URL,
                SUPABASE_ANON_KEY.length() > 10 ?
                        SUPABASE_ANON_KEY.substring(0, 10) + "..." :
                        SUPABASE_ANON_KEY
        );
    }
}

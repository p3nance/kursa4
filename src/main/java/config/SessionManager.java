package config;
public class SessionManager {
    private static String userId = null;
    private static String userEmail = null;
    private static String accessToken = null;

    public static void saveUserId(String id) { userId = id; }
    public static String getUserId() { return userId; }
    public static void saveUserEmail(String email) { userEmail = email; }
    public static String getUserEmail() { return userEmail; }
    public static void saveAccessToken(String token) { accessToken = token; }
    public static String getAccessToken() { return accessToken; }
    public static void clearSession() {
        userId = null; userEmail = null; accessToken = null;
    }
    public static boolean isAuthenticated() {
        return accessToken != null && userId != null;
    }
}

package config;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class SessionManager {

    private static final String TOKEN_FILE = System.getProperty("user.home") + "/.kursa4/session.txt";
    private static final String USER_ID_FILE = System.getProperty("user.home") + "/.kursa4/userId.txt";

    /**
     * Сохранить access token в файл
     */
    public static void saveAccessToken(String token) {
        try {
            // Создать директорию если её нет
            java.nio.file.Path dirPath = Paths.get(System.getProperty("user.home") + "/.kursa4");
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            // Сохранить токен
            Files.write(
                    Paths.get(TOKEN_FILE),
                    token.getBytes(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            System.out.println("✅ Токен сохранён");
        } catch (Exception e) {
            System.err.println("❌ Ошибка при сохранении токена: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Получить access token из файла
     */
    public static String getAccessToken() {
        try {
            java.nio.file.Path path = Paths.get(TOKEN_FILE);
            if (Files.exists(path)) {
                String token = new String(Files.readAllBytes(path)).trim();
                if (!token.isEmpty()) {
                    System.out.println("✅ Токен загружен из файла");
                    return token;
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Ошибка при чтении токена: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("⚠️ Токен не найден");
        return null;
    }

    /**
     * Сохранить ID пользователя
     */
    public static void saveUserId(String userId) {
        try {
            java.nio.file.Path dirPath = Paths.get(System.getProperty("user.home") + "/.kursa4");
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            Files.write(
                    Paths.get(USER_ID_FILE),
                    userId.getBytes(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            System.out.println("✅ ID пользователя сохранён");
        } catch (Exception e) {
            System.err.println("❌ Ошибка при сохранении ID: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Получить ID пользователя
     */
    public static String getUserId() {
        try {
            java.nio.file.Path path = Paths.get(USER_ID_FILE);
            if (Files.exists(path)) {
                String userId = new String(Files.readAllBytes(path)).trim();
                if (!userId.isEmpty()) {
                    System.out.println("✅ ID пользователя загружен");
                    return userId;
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Ошибка при чтении ID: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Очистить сессию при выходе
     */
    public static void clearAccessToken() {
        try {
            java.nio.file.Path tokenPath = Paths.get(TOKEN_FILE);
            java.nio.file.Path userIdPath = Paths.get(USER_ID_FILE);

            if (Files.exists(tokenPath)) {
                Files.delete(tokenPath);
            }
            if (Files.exists(userIdPath)) {
                Files.delete(userIdPath);
            }

            System.out.println("✅ Сессия очищена");
        } catch (Exception e) {
            System.err.println("❌ Ошибка при очистке сессии: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Проверить, авторизован ли пользователь
     */
    public static boolean isAuthenticated() {
        return getAccessToken() != null && getUserId() != null;
    }
}

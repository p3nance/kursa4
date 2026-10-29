package com.example.authapp.services;

import com.example.authapp.network.SupabaseHttp;
import config.Config;
import config.SessionManager;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.UUID;

public final class SupabaseStorageService {
    private static final String BUCKET = "product-images";
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    private SupabaseStorageService() {}
    public static String uploadImage(File file, String fileName) throws Exception {
        SessionManager.ensureValidSession();
        validateFileName(fileName);
        if (file == null || !Files.isRegularFile(file.toPath())) throw new IllegalArgumentException("Файл изображения не найден");
        if (Files.size(file.toPath()) > MAX_IMAGE_BYTES) throw new IllegalArgumentException("Изображение должно быть не больше 10 МБ");
        byte[] bytes;
        try (InputStream stream = Files.newInputStream(file.toPath())) { bytes = stream.readNBytes(MAX_IMAGE_BYTES + 1); }
        if (bytes.length > MAX_IMAGE_BYTES) throw new IllegalArgumentException("Изображение должно быть не больше 10 МБ");
        String contentType = contentType(fileName);
        if (!matchesSignature(bytes, contentType)) throw new IllegalArgumentException("Содержимое файла не соответствует формату изображения");
        SupabaseHttp.send(SupabaseHttp.request("/storage/v1/object/" + BUCKET + "/" + fileName)
                .header("Content-Type", contentType).POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build());
        return Config.SUPABASE_URL + "/storage/v1/object/public/" + BUCKET + "/" + fileName;
    }
    public static void deleteImage(String fileName) throws Exception {
        SessionManager.ensureValidSession();
        validateFileName(fileName);
        SupabaseHttp.send(SupabaseHttp.request("/storage/v1/object/" + BUCKET + "/" + fileName).DELETE().build());
    }
    public static String extractFileNameFromUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return null;
        String prefix = Config.SUPABASE_URL + "/storage/v1/object/public/" + BUCKET + "/";
        if (!imageUrl.startsWith(prefix)) return null;
        try {
            String path = URI.create(imageUrl).getPath();
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            validateFileName(fileName); return fileName;
        } catch (IllegalArgumentException e) { return null; }
    }
    public static String generateFileName(int productId, String originalFileName) {
        if (originalFileName == null) throw new IllegalArgumentException("Имя файла не задано");
        int dot = originalFileName.lastIndexOf('.');
        if (dot < 0) throw new IllegalArgumentException("Неизвестный формат изображения");
        String extension = originalFileName.substring(dot).toLowerCase(Locale.ROOT);
        String result = "product_" + productId + "_" + UUID.randomUUID() + extension;
        validateFileName(result); contentType(result); return result;
    }
    public static void validateFileName(String fileName) {
        if (fileName == null || !fileName.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,199}") || fileName.contains("..")) {
            throw new IllegalArgumentException("Некорректное имя изображения");
        }
    }
    private static String contentType(String fileName) {
        String name = fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".gif")) return "image/gif";
        if (name.endsWith(".webp")) return "image/webp";
        throw new IllegalArgumentException("Поддерживаются JPG, PNG, GIF и WebP");
    }
    private static boolean matchesSignature(byte[] data, String type) {
        if (data.length < 12) return false;
        return switch (type) {
            case "image/jpeg" -> (data[0] & 255) == 255 && (data[1] & 255) == 216 && (data[2] & 255) == 255;
            case "image/png" -> (data[0] & 255) == 137 && data[1] == 80 && data[2] == 78 && data[3] == 71
                    && data[4] == 13 && data[5] == 10 && data[6] == 26 && data[7] == 10;
            case "image/gif" -> new String(data, 0, 6, StandardCharsets.US_ASCII).matches("GIF8[79]a");
            case "image/webp" -> new String(data, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                    && new String(data, 8, 4, StandardCharsets.US_ASCII).equals("WEBP");
            default -> false;
        };
    }
}

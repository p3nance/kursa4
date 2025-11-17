package com.example.authapp.services;

import config.Config;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;

/**
 * Сервис для работы с Supabase Storage
 */
public class SupabaseStorageService {

    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final String BUCKET_NAME = "product-images"; // Название вашего bucket в Supabase

    /**
     * Загружает изображение в Supabase Storage
     * @param file Файл изображения для загрузки
     * @param fileName Имя файла в хранилище (например, "product_123.jpg")
     * @return URL загруженного изображения или null в случае ошибки
     */
    public static String uploadImage(File file, String fileName) throws Exception {
        try {
            // Читаем файл в байты
            byte[] fileBytes = Files.readAllBytes(file.toPath());

            // Определяем Content-Type на основе расширения файла
            String contentType = getContentType(fileName);

            // URL для загрузки: /storage/v1/object/{bucket_name}/{file_path}
            String uploadUrl = Config.SUPABASE_URL + "/storage/v1/object/" + BUCKET_NAME + "/" + fileName;

            System.out.println("📤 Загрузка изображения: " + fileName);
            System.out.println("📍 URL: " + uploadUrl);

            // Создаем запрос
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uploadUrl))
                    .header("Authorization", "Bearer " + Config.SUPABASE_ANON_KEY)
                    .header("apikey", Config.SUPABASE_ANON_KEY)
                    .header("Content-Type", contentType)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(fileBytes))
                    .build();

            // Отправляем запрос
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("📊 Статус ответа: " + response.statusCode());
            System.out.println("📄 Ответ: " + response.body());

            if (response.statusCode() == 200 || response.statusCode() == 201) {
                // Формируем публичный URL изображения
                String publicUrl = Config.SUPABASE_URL + "/storage/v1/object/public/" + BUCKET_NAME + "/" + fileName;
                System.out.println("✅ Изображение загружено: " + publicUrl);
                return publicUrl;
            } else {
                throw new Exception("Ошибка загрузки изображения: " + response.statusCode() + " - " + response.body());
            }

        } catch (IOException e) {
            System.err.println("❌ Ошибка чтения файла: " + e.getMessage());
            throw new Exception("Ошибка чтения файла: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("❌ Ошибка загрузки в Supabase Storage: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Удаляет изображение из Supabase Storage
     * @param fileName Имя файла для удаления
     */
    public static void deleteImage(String fileName) throws Exception {
        try {
            String deleteUrl = Config.SUPABASE_URL + "/storage/v1/object/" + BUCKET_NAME + "/" + fileName;

            System.out.println("🗑️ Удаление изображения: " + fileName);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(deleteUrl))
                    .header("Authorization", "Bearer " + Config.SUPABASE_ANON_KEY)
                    .header("apikey", Config.SUPABASE_ANON_KEY)
                    .DELETE()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 || response.statusCode() == 204) {
                System.out.println("✅ Изображение удалено");
            } else {
                System.err.println("⚠️ Не удалось удалить изображение: " + response.statusCode());
            }

        } catch (Exception e) {
            System.err.println("❌ Ошибка удаления изображения: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Определяет Content-Type на основе расширения файла
     */
    private static String getContentType(String fileName) {
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lowerName.endsWith(".png")) {
            return "image/png";
        } else if (lowerName.endsWith(".gif")) {
            return "image/gif";
        } else if (lowerName.endsWith(".webp")) {
            return "image/webp";
        }
        return "application/octet-stream";
    }

    /**
     * Извлекает имя файла из полного URL
     * Например: "https://.../storage/v1/object/public/product-images/product_123.jpg" -> "product_123.jpg"
     */
    public static String extractFileNameFromUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return null;
        }

        String[] parts = imageUrl.split("/");
        if (parts.length > 0) {
            return parts[parts.length - 1];
        }

        return null;
    }

    /**
     * Генерирует уникальное имя файла на основе ID товара и текущего времени
     */
    public static String generateFileName(int productId, String originalFileName) {
        // Получаем расширение файла
        String extension = "";
        int dotIndex = originalFileName.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = originalFileName.substring(dotIndex);
        }

        // Генерируем имя: product_{id}_{timestamp}{extension}
        return "product_" + productId + "_" + System.currentTimeMillis() + extension;
    }
}
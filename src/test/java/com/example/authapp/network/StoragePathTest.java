package com.example.authapp.network;

import com.example.authapp.services.SupabaseStorageService;
import config.Config;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StoragePathTest {
    @Test void rejectsTraversalAndNestedPaths() {
        assertThrows(IllegalArgumentException.class, () -> SupabaseStorageService.validateFileName("../avatar.png"));
        assertThrows(IllegalArgumentException.class, () -> SupabaseStorageService.validateFileName("folder/avatar.png"));
        assertThrows(IllegalArgumentException.class, () -> SupabaseStorageService.validateFileName("image.png?x=1"));
        assertDoesNotThrow(() -> SupabaseStorageService.validateFileName("product_1_abc.png"));
    }
    @Test void onlyDeletesOwnBucketAssets() {
        assertNull(SupabaseStorageService.extractFileNameFromUrl("https://example.com/photo.png"));
        assertEquals("photo.png", SupabaseStorageService.extractFileNameFromUrl(Config.SUPABASE_URL + "/storage/v1/object/public/product-images/photo.png"));
        assertThrows(IllegalArgumentException.class, () -> SupabaseStorageService.generateFileName(1, "payload.exe"));
    }
}

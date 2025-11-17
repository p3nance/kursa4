package com.example.authapp.services;

import com.example.authapp.dto.ProductDTO;
import com.example.authapp.repositories.AdminRepository;
import controllers.AdminController;
import javafx.application.Platform;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * ✅ Сервис автоматической синхронизации список товаров в админ-панели
 * Каждые N секунд проверяет обновления в базе данных и обновляет таблицу
 */
public class AdminRefreshService {

    private static final long REFRESH_INTERVAL = 10000; // 10 секунд между проверками
    private AdminController adminController;
    private Timer refreshTimer;
    private boolean isRunning = false;

    public AdminRefreshService(AdminController adminController) {
        this.adminController = adminController;
    }

    /**
     * ✅ ЗАПУСКАЕТ СЕРВИС АВТОМАТИЧЕСКОГО ОБНОВЛЕНИЯ
     */
    public void start() {
        if (isRunning) {
            System.out.println("⏸️ Сервис синхронизации уже запущен");
            return;
        }

        System.out.println("▶️ Запуск сервиса синхронизации админ-панели");
        System.out.println("⏱️ Интервал обновления: " + REFRESH_INTERVAL + "ms");

        isRunning = true;
        refreshTimer = new Timer("AdminRefreshService", true);

        // ✅ Задача синхронизации каждые REFRESH_INTERVAL миллисекунд
        refreshTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    if (isRunning) {
                        System.out.println("🔄 [" + System.currentTimeMillis() % 100000 + "] Проверка обновлений товаров...");

                        // ✅ ИСПРАВЛЕНО: вызываем публичный метод
                        if (adminController != null) {
                            adminController.refreshProductsList();
                        }

                        System.out.println("✅ Синхронизация завершена");
                    }
                } catch (Exception e) {
                    System.err.println("⚠️ Ошибка синхронизации: " + e.getMessage());
                }
            }
        }, REFRESH_INTERVAL, REFRESH_INTERVAL);
    }

    /**
     * ✅ ОСТАНАВЛИВАЕТ СЕРВИС
     */
    public void stop() {
        if (!isRunning) {
            return;
        }

        System.out.println("⏹️ Остановка сервиса синхронизации");
        isRunning = false;

        if (refreshTimer != null) {
            refreshTimer.cancel();
            refreshTimer.purge();
        }
    }

    /**
     * ✅ ПРОВЕРЯЕТ, ЗАПУЩЕН ЛИ СЕРВИС
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * ✅ ПРИНУДИТЕЛЬНОЕ ОБНОВЛЕНИЕ (независимо от интервала)
     */
    public void refreshNow() {
        System.out.println("🔄 Принудительное обновление...");
        try {
            if (adminController != null) {
                // ✅ ИСПРАВЛЕНО: вызываем публичный метод
                adminController.refreshProductsList();
            }
            System.out.println("✅ Принудительное обновление завершено");
        } catch (Exception e) {
            System.err.println("❌ Ошибка при принудительном обновлении: " + e.getMessage());
        }
    }
}
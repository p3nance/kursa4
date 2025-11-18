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


        isRunning = true;
        refreshTimer = new Timer("AdminRefreshService", true);

        // ✅ Задача синхронизации каждые REFRESH_INTERVAL миллисекунд
        refreshTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    if (isRunning) {

                        if (adminController != null) {
                            adminController.refreshProductsList();
                        }

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

        isRunning = false;

        if (refreshTimer != null) {
            refreshTimer.cancel();
            refreshTimer.purge();
        }
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void refreshNow() {
        try {
            if (adminController != null) {
                // ✅ ИСПРАВЛЕНО: вызываем публичный метод
                adminController.refreshProductsList();
            }
        } catch (Exception e) {
            System.err.println("❌ Ошибка при принудительном обновлении: " + e.getMessage());
        }
    }
}
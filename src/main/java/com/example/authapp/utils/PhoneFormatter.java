package com.example.authapp.utils;

import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.util.converter.DefaultStringConverter;


public class PhoneFormatter {

    private static final int MAX_LENGTH = 12; // +7 (2) + 10 цифр = 12

    public static void setupPhoneField(TextField phoneField) {
        TextFormatter<String> formatter = new TextFormatter<>(new DefaultStringConverter(), "", change -> {
            String newText = change.getControlNewText();

            // Если пусто - разрешаем
            if (newText.isEmpty()) {
                return change;
            }

            // Если только ввожу первый символ - должен быть +
            if (newText.length() == 1) {
                if (newText.equals("+")) {
                    return change;
                } else {
                    return null; // Отклоняем
                }
            }

            // Если вводим 2-й символ - должен быть 7
            if (newText.length() == 2) {
                if (newText.equals("+7")) {
                    return change;
                } else {
                    return null;
                }
            }

            // После "+7" только цифры
            if (newText.length() > 2 && newText.length() <= MAX_LENGTH) {
                // Проверяем, что после +7 идут только цифры
                String digits = newText.substring(2);
                if (digits.matches("\\d*")) {
                    return change;
                } else {
                    return null;
                }
            }

            // Больше 12 символов - не разрешаем
            if (newText.length() > MAX_LENGTH) {
                return null;
            }

            return change;
        });

        phoneField.setTextFormatter(formatter);

        // Визуальная обратная связь при потере фокуса
        phoneField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal && !phoneField.getText().isEmpty()) {
                if (!isCompletePhone(phoneField.getText())) {
                    phoneField.setStyle("-fx-border-color: #fbbf24; -fx-border-width: 2;");
                } else {
                    phoneField.setStyle("-fx-border-color: #10b981; -fx-border-width: 2;");
                }
            } else if (newVal) {
                phoneField.setStyle("");
            }
        });
    }

    public static boolean isCompletePhone(String phone) {
        if (phone == null) {
            return false;
        }

        // Должна быть ровно 12 символов (+7 + 10 цифр)
        if (phone.length() != MAX_LENGTH) {
            return false;
        }

        // Должна начинаться с +7
        if (!phone.startsWith("+7")) {
            return false;
        }

        // После +7 только цифры и ровно 10 цифр
        String digits = phone.substring(2);
        return digits.matches("\\d{10}");
    }

    public static boolean isValidPhoneInput(String phone) {
        if (phone == null || phone.isEmpty()) {
            return true;
        }

        // Первый символ должен быть +
        if (phone.length() >= 1 && !phone.startsWith("+")) {
            return false;
        }

        // Если 2+ символов - должны начинаться с +7
        if (phone.length() >= 2 && !phone.startsWith("+7")) {
            return false;
        }

        // Не больше 12 символов
        if (phone.length() > MAX_LENGTH) {
            return false;
        }

        // После +7 только цифры
        if (phone.length() > 2) {
            String digits = phone.substring(2);
            if (!digits.matches("\\d*")) {
                return false;
            }
        }

        return true;
    }

    public static String getPhoneHint() {
        return "Формат: +79878073394 (12 символов)";
    }

    public static String sanitizePhone(String phone) {
        if (phone == null) {
            return "";
        }

        // Удаляем все кроме + и цифр
        String cleaned = phone.replaceAll("[^+\\d]", "");

        // Если не начинается с +7, добавляем
        if (!cleaned.startsWith("+7")) {
            cleaned = "+7" + cleaned.replaceAll("\\D", "");
        }

        // Ограничиваем до 12 символов
        if (cleaned.length() > MAX_LENGTH) {
            cleaned = cleaned.substring(0, MAX_LENGTH);
        }

        return cleaned;
    }
}
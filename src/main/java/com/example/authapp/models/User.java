package com.example.authapp.models;

public class User {
    private String id;
    private String email;
    private String createdAt;

    public User() {}

    public User(String id, String email, String createdAt) {
        this.id = id;
        this.email = email;
        this.createdAt = createdAt;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
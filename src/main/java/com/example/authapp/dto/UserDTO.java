package com.example.authapp.dto;

import com.google.gson.annotations.SerializedName;

public class UserDTO {
    @SerializedName("id")
    public String id;

    @SerializedName("name")
    public String name;

    @SerializedName("surname")
    public String surname;

    @SerializedName("email")
    public String email;

    @SerializedName("phone")
    public String phone;

    @SerializedName("city")
    public String city;

    @SerializedName("address")
    public String address;

    @SerializedName("created_at")
    public String created_at;

    @SerializedName("updated_at")
    public String updated_at;

    @SerializedName("is_admin")
    public boolean is_admin;

    @SerializedName("is_blocked")
    public boolean is_blocked = false;

    @SerializedName("role")
    public String role;

    public UserDTO() {}

    @Override
    public String toString() {
        return "UserDTO{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", surname='" + surname + '\'' +
                ", email='" + email + '\'' +
                ", phone='" + phone + '\'' +
                ", city='" + city + '\'' +
                ", address='" + address + '\'' +
                ", is_admin=" + is_admin +
                ", is_blocked=" + is_blocked +
                '}';
    }
}
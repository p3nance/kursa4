package com.example.authapp.dto;

public class UserDTO {
    public String id;
    public String name;
    public String surname;
    public String email;
    public String phone;
    public String city;
    public String address;
    public String created_at;
    public String updated_at;

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
                '}';
    }
}
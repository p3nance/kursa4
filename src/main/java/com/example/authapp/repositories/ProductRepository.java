package com.example.authapp.repositories;

import com.example.authapp.models.Product;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ProductRepository {
    private static final String SUPABASE_URL = "https://qsthuhzkciimucarscco.supabase.co/rest/v1/products";
    private static final String API_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InFzdGh1aHprY2lpbXVjYXJzY2NvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTc0MjM4MTUsImV4cCI6MjA3Mjk5OTgxNX0.VnHrSq-S8NlSmzQ7_soRvrc7t3s3fEp_wu9tTwm9ZUI";
    public static List<Product> loadProductsFromSupabase() throws IOException, JSONException {
        URL url = new URL(SUPABASE_URL + "?select=*");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("apikey", API_KEY);
        conn.setRequestProperty("Authorization", "Bearer " + API_KEY);

        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder content = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) content.append(line);
        in.close();
        List<Product> products = new ArrayList<>();
        JSONArray arr = new JSONArray(content.toString());
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            // Порядок аргументов как в Product!
            products.add(new Product(
                    obj.getString("name"),
                    obj.optString("description", ""),
                    obj.getDouble("price"),
                    obj.optString("category", ""),
                    obj.optString("manufacturer", ""),
                    obj.optString("image_url", "")
            ));
        }
        return products;
    }
}

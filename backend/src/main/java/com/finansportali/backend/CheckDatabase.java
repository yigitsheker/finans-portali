package com.finansportali.backend;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class CheckDatabase {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/finans";
        String user = "finans";
        String password = "finans";
        
        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement()) {
            
            System.out.println("=== Checking news_articles table columns ===");
            ResultSet rs = stmt.executeQuery(
                "SELECT column_name, data_type FROM information_schema.columns " +
                "WHERE table_name = 'news_articles' ORDER BY ordinal_position"
            );
            
            while (rs.next()) {
                System.out.println("  - " + rs.getString("column_name") + " (" + rs.getString("data_type") + ")");
            }
            
            System.out.println("\n=== Checking if exchange_rates table exists ===");
            ResultSet rs2 = stmt.executeQuery(
                "SELECT EXISTS (SELECT FROM information_schema.tables " +
                "WHERE table_name = 'exchange_rates')"
            );
            if (rs2.next()) {
                System.out.println("  exchange_rates exists: " + rs2.getBoolean(1));
            }
            
            System.out.println("\n=== Checking Flyway history ===");
            ResultSet rs3 = stmt.executeQuery(
                "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank"
            );
            while (rs3.next()) {
                System.out.println("  V" + rs3.getString("version") + ": " + 
                    rs3.getString("description") + " (success=" + rs3.getBoolean("success") + ")");
            }
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

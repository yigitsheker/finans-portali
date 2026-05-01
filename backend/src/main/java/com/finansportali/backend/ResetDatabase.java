package com.finansportali.backend;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * Utility to completely reset the database.
 * WARNING: This will delete ALL data!
 */
public class ResetDatabase {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/finans";
        String user = "finans";
        String password = "finans";
        
        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement()) {
            
            System.out.println("⚠️  WARNING: This will delete ALL data!");
            System.out.println("Dropping all tables...");
            
            // Drop all tables in correct order (respecting foreign keys)
            stmt.execute("DROP TABLE IF EXISTS price_alerts CASCADE");
            stmt.execute("DROP TABLE IF EXISTS portfolio_positions CASCADE");
            stmt.execute("DROP TABLE IF EXISTS market_candles CASCADE");
            stmt.execute("DROP TABLE IF EXISTS market_quotes CASCADE");
            stmt.execute("DROP TABLE IF EXISTS news_articles CASCADE");
            stmt.execute("DROP TABLE IF EXISTS market_instruments CASCADE");
            stmt.execute("DROP TABLE IF EXISTS exchange_rates CASCADE");
            stmt.execute("DROP TABLE IF EXISTS investment_funds CASCADE");
            stmt.execute("DROP TABLE IF EXISTS flyway_schema_history CASCADE");
            
            System.out.println("✓ All tables dropped");
            System.out.println("✓ Database reset complete");
            System.out.println("\nYou can now restart the application and Flyway will recreate everything.");
            
        } catch (Exception e) {
            System.err.println("✗ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

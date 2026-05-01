package com.finansportali.backend;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * Temporary utility to fix Flyway migration history.
 * Run this once to remove failed V2 migration record.
 */
public class FixFlywayHistory {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/finans";
        String user = "finans";
        String password = "finans";
        
        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement()) {
            
            // Delete failed V2 migration
            int deleted = stmt.executeUpdate(
                "DELETE FROM flyway_schema_history WHERE version = '2'"
            );
            
            System.out.println("✓ Deleted " + deleted + " failed migration record(s)");
            System.out.println("✓ Flyway history cleaned. You can now restart the application.");
            
        } catch (Exception e) {
            System.err.println("✗ Error: " + e.getMessage());
            System.err.println("\nPlease check:");
            System.err.println("1. PostgreSQL is running");
            System.err.println("2. Database 'finans' exists");
            System.err.println("3. User 'finans' has correct password");
            e.printStackTrace();
        }
    }
}

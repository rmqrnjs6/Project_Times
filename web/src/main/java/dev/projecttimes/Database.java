package dev.projecttimes;
import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;

public final class Database {
    private final String url = System.getenv("DB_URL");
    private final String user = System.getenv("DB_USER");
    private final String password = System.getenv("DB_PASSWORD");
    public void migrate() { Flyway.configure().dataSource(url, user, password).load().migrate(); }
    private Connection connect() throws SQLException { return DriverManager.getConnection(url, user, password); }
    public void ready() throws SQLException {
        try (var c = connect(); var s = c.createStatement(); var r = s.executeQuery("SELECT 1")) {
            if (!r.next() || r.getInt(1) != 1) throw new SQLException("database not ready");
        }
    }
    public UUID saveCheck(String question) throws SQLException {
        UUID id = UUID.randomUUID();
        try (var c = connect(); var s = c.prepareStatement("INSERT INTO diagnostic_checks(id, question) VALUES (?, ?)")) {
            s.setObject(1, id); s.setString(2, question); s.executeUpdate();
        }
        return id;
    }
    public List<Map<String, String>> checks() throws SQLException {
        var rows = new ArrayList<Map<String, String>>();
        try (var c = connect(); var s = c.createStatement(); var r = s.executeQuery("SELECT id, question, created_at, kind FROM diagnostic_checks ORDER BY created_at DESC LIMIT 50")) {
            while (r.next()) rows.add(Map.of("id", r.getString(1), "question", r.getString(2), "created_at", r.getString(3), "kind", r.getString(4)));
        }
        return rows;
    }
    public UUID saveCalculation(String policy, String input, String result) throws SQLException {
        UUID id = UUID.randomUUID();
        try (var c = connect(); var s = c.prepareStatement("INSERT INTO calculation_runs(id, policy_version, input, result) VALUES (?, ?, ?::jsonb, ?::jsonb)")) {
            s.setObject(1, id); s.setString(2, policy); s.setString(3, input); s.setString(4, result); s.executeUpdate();
        }
        return id;
    }
}

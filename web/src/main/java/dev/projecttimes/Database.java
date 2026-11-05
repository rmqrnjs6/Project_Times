package dev.projecttimes;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.stereotype.Repository;

@Repository
public class Database {
    private final DataSource dataSource;
    public Database(DataSource dataSource) { this.dataSource = dataSource; }
    private Connection connect() throws SQLException { return dataSource.getConnection(); }
    public void ready() throws SQLException {
        try (var c = connect(); var s = c.createStatement(); var r = s.executeQuery("SELECT 1")) {
            if (!r.next() || r.getInt(1) != 1) throw new SQLException("database not ready");
        }
    }
    public UUID saveCheck(String owner, String question) throws SQLException {
        UUID id = UUID.randomUUID();
        try (var c = connect(); var s = c.prepareStatement("INSERT INTO diagnostic_checks(id, owner_key, question) VALUES (?, ?, ?)")) {
            s.setObject(1, id); s.setString(2, owner); s.setString(3, question); s.executeUpdate();
        }
        return id;
    }
    public List<Map<String, String>> checks(String owner) throws SQLException {
        var rows = new ArrayList<Map<String, String>>();
        try (var c = connect(); var s = c.prepareStatement("SELECT id, question, created_at, kind FROM diagnostic_checks WHERE owner_key = ? ORDER BY created_at DESC LIMIT 50")) {
            s.setString(1, owner);
            try (var r = s.executeQuery()) {
            while (r.next()) rows.add(Map.of("id", r.getString(1), "question", r.getString(2), "created_at", r.getString(3), "kind", r.getString(4)));
            }
        }
        return rows;
    }
    public UUID saveCalculation(String owner, String policy, String input, String result) throws SQLException {
        UUID id = UUID.randomUUID();
        try (var c = connect(); var s = c.prepareStatement("INSERT INTO calculation_runs(id, owner_key, policy_version, input, result) VALUES (?, ?, ?, ?::jsonb, ?::jsonb)")) {
            s.setObject(1, id); s.setString(2, owner); s.setString(3, policy); s.setString(4, input); s.setString(5, result); s.executeUpdate();
        }
        return id;
    }
    public UUID saveMaterial(String owner, String source, String content, String hash) throws SQLException {
        UUID id = UUID.randomUUID();
        try (var c = connect(); var s = c.prepareStatement("INSERT INTO external_materials(id,owner_key,source_url,raw_text,sha256,status) VALUES (?,?,?,?,?,'quarantined')")) {
            s.setObject(1,id); s.setString(2,owner); s.setString(3,source); s.setString(4,content); s.setString(5,hash); s.executeUpdate();
        }
        return id;
    }
}

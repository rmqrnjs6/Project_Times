package dev.projecttimes;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
/** Persistent, atomic per-remote-address budget; never trusts X-Forwarded-For. */
@Repository
public class LoginAttemptStore {
    private final JdbcTemplate jdbc;
    public LoginAttemptStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    private String key(String address) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(address.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    public boolean allow(String address) {
        jdbc.update("DELETE FROM login_attempts WHERE window_started < CURRENT_TIMESTAMP - INTERVAL '1 day'");
        Integer attempts=jdbc.queryForObject("""
            INSERT INTO login_attempts(remote_key,window_started,attempts) VALUES (?,CURRENT_TIMESTAMP,1)
            ON CONFLICT(remote_key) DO UPDATE SET
              attempts=CASE WHEN login_attempts.window_started < CURRENT_TIMESTAMP-INTERVAL '15 minutes' THEN 1 ELSE login_attempts.attempts+1 END,
              window_started=CASE WHEN login_attempts.window_started < CURRENT_TIMESTAMP-INTERVAL '15 minutes' THEN CURRENT_TIMESTAMP ELSE login_attempts.window_started END
            RETURNING attempts
            """,Integer.class,key(address));
        return attempts!=null && attempts<=10;
    }
    public void reset(String address) { jdbc.update("DELETE FROM login_attempts WHERE remote_key=?",key(address)); }
}

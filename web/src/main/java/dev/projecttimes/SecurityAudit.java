package dev.projecttimes;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class SecurityAudit {
    private final JdbcTemplate jdbc;
    public SecurityAudit(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public void record(String type,String actor) { jdbc.update("INSERT INTO security_events(event_type,actor) VALUES (?,?)",type,actor); }
}

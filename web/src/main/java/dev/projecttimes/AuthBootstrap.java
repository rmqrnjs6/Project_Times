package dev.projecttimes;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
@Component
@ConditionalOnProperty(name="app.bootstrap.enabled",havingValue="true",matchIfMissing=true)
public class AuthBootstrap implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;
    public AuthBootstrap(JdbcTemplate jdbc,PasswordEncoder encoder,@Value("${app.bootstrap.username}") String username,@Value("${app.bootstrap.password}") String password) {
        this.jdbc=jdbc; this.encoder=encoder; this.username=username; this.password=password;
    }
    @Transactional
    public void run(ApplicationArguments args) {
        if (!username.matches("[a-zA-Z0-9_-]{3,64}")) throw new IllegalStateException("Invalid bootstrap username");
        if (jdbc.queryForObject("SELECT count(*) FROM security_users WHERE username=?",Integer.class,username)>0) return;
        int bytes=password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (bytes<16 || bytes>64 || password.startsWith("replace-")) throw new IllegalStateException("Set a bootstrap password of 16-64 UTF-8 bytes; no default account is created");
        jdbc.update("INSERT INTO security_users(username,password,enabled) VALUES (?,?,true)",username,encoder.encode(password));
        jdbc.update("INSERT INTO security_authorities(username,authority) VALUES (?,'ROLE_ADMIN')",username);
    }
}

package dev.projecttimes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AuthBootstrapTest {
    @ParameterizedTest @ValueSource(strings={"","short","replace-with-a-known-example-password"})
    void refusesMissingWeakOrExampleCredentials(String password) {
        var jdbc=mock(JdbcTemplate.class); var encoder=mock(PasswordEncoder.class);
        when(jdbc.queryForObject("SELECT count(*) FROM security_users WHERE username=?",Integer.class,"owner")).thenReturn(0);
        assertThrows(IllegalStateException.class,()->new AuthBootstrap(jdbc,encoder,"owner",password).run(null));
        verifyNoInteractions(encoder);
    }
    @Test void existingAccountIsNotSilentlyReset() {
        var jdbc=mock(JdbcTemplate.class); var encoder=mock(PasswordEncoder.class);
        when(jdbc.queryForObject("SELECT count(*) FROM security_users WHERE username=?",Integer.class,"owner")).thenReturn(1);
        new AuthBootstrap(jdbc,encoder,"owner","synthetic-different-bootstrap-password").run(null);
        verifyNoInteractions(encoder);
    }
}

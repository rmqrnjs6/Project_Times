package dev.projecttimes;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
class ExternalMaterialPolicyTest {
    final ExternalMaterialPolicy policy=new ExternalMaterialPolicy("example.org");
    @ParameterizedTest
    @ValueSource(strings={"http://example.org/a","file:///etc/passwd","https://127.0.0.1/a","https://[::1]/a","https://localhost/a","https://example.org.evil.test/a","https://user:pass@example.org/a","https://example.org/a?token=secret","https://example.org/a#fragment","https://example.org:8443/a","https://example.org./a"})
    void unsafeSourcesRejected(String source) { assertThrows(RequestFailure.class,()->policy.inspect(source,"raw text","text/plain")); }
    @Test void defaultIsDeny() { assertThrows(RequestFailure.class,()->new ExternalMaterialPolicy("").inspect("https://example.org/a","raw text","text/plain")); }
    @Test void exactHashOfUtf8BytesPreserved() { assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",policy.inspect("https://example.org/a","abc","text/plain").sha256()); }
    @Test void binaryAndOversizedContentRejected() {
        assertThrows(RequestFailure.class,()->policy.inspect("https://example.org/a","text","application/pdf"));
        assertThrows(RequestFailure.class,()->policy.inspect("https://example.org/a","x".repeat(20001),"text/plain"));
        assertThrows(RequestFailure.class,()->policy.inspect("https://example.org/a","x\0x","text/plain"));
    }
}

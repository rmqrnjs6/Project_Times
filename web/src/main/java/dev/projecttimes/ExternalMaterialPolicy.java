package dev.projecttimes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
/** Manual text intake only. Never resolves DNS, follows redirects or fetches a URL. */
@Component
public class ExternalMaterialPolicy {
    private final Set<String> allowed;
    public ExternalMaterialPolicy(@Value("${app.external.allowed-hosts:}") String hosts) {
        allowed=new HashSet<>();
        for (String host:hosts.split(",")) if (!host.isBlank()) allowed.add(host.strip().toLowerCase(Locale.ROOT));
    }
    public record Accepted(String source,String sha256) {}
    public Accepted inspect(String source,String content,String mediaType) {
        if (!"text/plain".equals(mediaType) || content==null || content.isBlank() || content.getBytes(StandardCharsets.UTF_8).length>20000 || content.indexOf('\0')>=0)
            throw new RequestFailure(400,"invalid_plain_text");
        try {
            if (source==null || source.length()>2048) throw new IllegalArgumentException();
            URI uri=new URI(source);
            String host=uri.getHost();
            if (!"https".equals(uri.getScheme()) || host==null || uri.getRawUserInfo()!=null || uri.getRawQuery()!=null || uri.getRawFragment()!=null || (uri.getPort()!=-1 && uri.getPort()!=443)) throw new IllegalArgumentException();
            host=host.toLowerCase(Locale.ROOT);
            if (host.endsWith(".") || host.contains(":") || host.matches("[0-9.]+") || host.equals("localhost") || host.endsWith(".localhost") || !allowed.contains(host)) throw new IllegalArgumentException();
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
            return new Accepted(uri.toASCIIString(),hash);
        } catch (Exception e) { throw new RequestFailure(400,"source_not_allowed"); }
    }
}

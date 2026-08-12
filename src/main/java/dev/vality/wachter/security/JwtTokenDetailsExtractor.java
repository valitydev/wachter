package dev.vality.wachter.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Optional;

public final class JwtTokenDetailsExtractor {

    private JwtTokenDetailsExtractor() {
    }

    public static Optional<JwtTokenDetails> extract(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            return Optional.empty();
        }
        var token = jwtAuthentication.getToken();
        return Optional.of(new JwtTokenDetails(
                token.getSubject(),
                token.getClaimAsString("preferred_username"),
                token.getClaimAsString("email"),
                extractRealm(token),
                jwtAuthentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .toList()));
    }

    private static String extractRealm(Jwt token) {
        var issuer = token.getClaimAsString(JwtClaimNames.ISS);
        if (issuer == null || issuer.isBlank()) {
            return null;
        }
        var normalized = issuer.replaceAll("/+$", "");
        var lastSlash = normalized.lastIndexOf('/');
        var realm = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
        return realm.isBlank() ? null : realm;
    }

    public record JwtTokenDetails(
            String subject,
            String preferredUsername,
            String email,
            String realm,
            List<String> roles) {
    }
}

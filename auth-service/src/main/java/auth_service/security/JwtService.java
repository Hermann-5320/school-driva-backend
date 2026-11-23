package auth_service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class JwtService {

    private static final String TYPE_ACCES = "ACCES";
    private static final String TYPE_UPLOAD = "UPLOAD";
    // 48 h : le chauffeur peut compléter son dossier plus tard (lien reçu par email)
    private static final long DUREE_UPLOAD_MS = 48 * 60 * 60 * 1000L;

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration}")
    private Long expiration;

    // Token d'accès classique
    public String genererToken(String email, String role, Long userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        claims.put("userId", userId);
        claims.put("type", TYPE_ACCES);

        return Jwts.builder()
                .claims(claims)
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getCle())
                .compact();
    }

    // Jeton court, remis après la pré-inscription, qui autorise UNIQUEMENT
    // l'envoi des documents du chauffeur concerné (pas de connexion possible).
    public String genererTokenUpload(Long chauffeurId) {
        return Jwts.builder()
                .claim("type", TYPE_UPLOAD)
                .claim("chauffeurId", chauffeurId)
                .subject("upload:" + chauffeurId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + DUREE_UPLOAD_MS))
                .signWith(getCle())
                .compact();
    }

    /** Retourne l'id chauffeur si le jeton d'upload est valide, sinon null. */
    public Long extraireChauffeurIdUpload(String token) {
        try {
            Claims claims = extraireClaims(token);
            if (!TYPE_UPLOAD.equals(claims.get("type", String.class))) return null;
            return claims.get("chauffeurId", Long.class);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean estTokenAcces(String token) {
        // Les anciens tokens sans "type" sont considérés comme des tokens d'accès
        String type = extraireClaims(token).get("type", String.class);
        return type == null || TYPE_ACCES.equals(type);
    }

    public String extraireEmail(String token) {
        return extraireClaims(token).getSubject();
    }

    public String extraireRole(String token) {
        return extraireClaims(token).get("role", String.class);
    }

    public Long extraireUserId(String token) {
        return extraireClaims(token).get("userId", Long.class);
    }

    public boolean estValide(String token, String email) {
        return extraireEmail(token).equals(email) && !estExpire(token);
    }

    private boolean estExpire(String token) {
        return extraireClaims(token).getExpiration().before(new Date());
    }

    private Claims extraireClaims(String token) {
        return Jwts.parser()
                .verifyWith(getCle())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getCle() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}

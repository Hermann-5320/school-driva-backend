package auth_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Protège les endpoints appelés uniquement par les autres services
 * (course-service, paiement-service) via la clé partagée X-Internal-Api-Key.
 * Sur Render (offre gratuite, pas de réseau privé), ces endpoints sont
 * joignables depuis Internet : sans cette clé, n'importe qui pourrait
 * modifier la note d'un chauffeur ou lire le téléphone d'un passager.
 */
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    public static final String[] PATTERNS_INTERNES = {
            "/api/chauffeurs/*/stats",
            "/api/chauffeurs/by-utilisateur/**",
            "/api/passagers/by-utilisateur/**",
            "/api/passagers/*/public",
            "/api/interne/**"
    };

    @Value("${app.internal-api-key}")
    private String internalApiKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (estInterne(request.getRequestURI())) {
            String cle = request.getHeader("X-Internal-Api-Key");
            if (cle == null || !MessageDigest.isEqual(
                    cle.getBytes(StandardCharsets.UTF_8),
                    internalApiKey.getBytes(StandardCharsets.UTF_8))) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"Accès interne non autorisé\"}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    public static boolean estInterne(String path) {
        for (String pattern : PATTERNS_INTERNES) {
            if (PATH_MATCHER.match(pattern, path)) return true;
        }
        return false;
    }
}

package paiement_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Protège les endpoints internes (appelés uniquement par d'autres services,
 * jamais par un utilisateur final) via une clé partagée envoyée dans
 * l'en-tête X-Internal-Api-Key.
 *
 * Ces endpoints n'ont pas de JWT utilisateur associé (ils sont appelés
 * service-à-service), donc ils restent permitAll côté Spring Security,
 * mais sans cette clé la requête est rejetée ici avant d'atteindre le
 * contrôleur.
 */
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final String[] PROTECTED_PATTERNS = {
            "/api/paiements/interne/**",
            "/api/paiements/verifier-solde/**"
    };

    @Value("${app.internal-api-key}")
    private String internalApiKey;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();
        boolean estProtege = false;
        for (String pattern : PROTECTED_PATTERNS) {
            if (PATH_MATCHER.match(pattern, path)) {
                estProtege = true;
                break;
            }
        }

        if (estProtege) {
            String cle = request.getHeader("X-Internal-Api-Key");
            if (cle == null || !cle.equals(internalApiKey)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"erreur\":\"Accès interne non autorisé\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}

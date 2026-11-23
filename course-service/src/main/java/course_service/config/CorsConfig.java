package course_service.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CORS de secours sur course-service (le gateway reste le point d'entrée normal).
 * CORS_ORIGINS accepte plusieurs origines séparées par des virgules ;
 * seule l'origine de la requête est renvoyée si elle est autorisée.
 */
@Component
@Order(Integer.MIN_VALUE)
public class CorsConfig implements Filter {

    private final Set<String> originesAutorisees;

    public CorsConfig(@Value("${app.cors.origins:http://localhost:5173}") String origines) {
        this.originesAutorisees = Arrays.stream(origines.split(","))
                .map(String::trim).filter(o -> !o.isEmpty()).collect(Collectors.toSet());
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String origine = request.getHeader("Origin");
        if (origine != null && originesAutorisees.contains(origine)) {
            response.setHeader("Access-Control-Allow-Origin", origine);
            response.setHeader("Vary", "Origin");
            response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Upload-Token");
            response.setHeader("Access-Control-Max-Age", "3600");
        }

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
        } else {
            chain.doFilter(req, res);
        }
    }
}

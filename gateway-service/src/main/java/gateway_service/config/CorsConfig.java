package gateway_service.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CORS centralisé au gateway. CORS_ORIGINS accepte plusieurs origines
 * séparées par des virgules (ex : "https://schooldriva.onrender.com,http://localhost:5173").
 * Les en-têtes CORS renvoyés par les services en aval sont ignorés pour
 * éviter les doublons ; seul le gateway décide.
 */
@Component
@Order(Integer.MIN_VALUE)
public class CorsConfig implements Filter {

    private static final Set<String> EN_TETES_CORS = Set.of(
            "access-control-allow-origin",
            "access-control-allow-methods",
            "access-control-allow-headers",
            "access-control-allow-credentials",
            "access-control-max-age",
            "vary"
    );

    private final Set<String> originesAutorisees;

    public CorsConfig(@Value("${app.cors.origins:http://localhost:5173}") String origines) {
        this.originesAutorisees = Arrays.stream(origines.split(","))
                .map(String::trim).filter(o -> !o.isEmpty()).collect(Collectors.toSet());
    }

    private static class ReponseSansCorsAval extends HttpServletResponseWrapper {
        ReponseSansCorsAval(HttpServletResponse response) { super(response); }

        @Override
        public void addHeader(String name, String value) {
            if (!EN_TETES_CORS.contains(name.toLowerCase())) super.addHeader(name, value);
        }

        @Override
        public void setHeader(String name, String value) {
            if (!EN_TETES_CORS.contains(name.toLowerCase())) super.setHeader(name, value);
        }
    }

    private void ajouterCors(HttpServletRequest request, HttpServletResponse response) {
        String origine = request.getHeader("Origin");
        if (origine != null && originesAutorisees.contains(origine)) {
            response.setHeader("Access-Control-Allow-Origin", origine);
            response.setHeader("Vary", "Origin");
            response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Upload-Token");
            response.setHeader("Access-Control-Max-Age", "3600");
        }
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        // En-têtes posés AVANT la réponse aval (sinon perdus une fois la réponse envoyée)
        ajouterCors(request, response);

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        chain.doFilter(req, new ReponseSansCorsAval(response));
    }
}

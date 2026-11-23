package paiement_service.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class AuthServiceClient {

    @Value("${app.auth-service.url}")
    private String authServiceUrl;

    @Value("${app.internal-api-key}")
    private String internalApiKey;

    private final RestTemplate restTemplate = creerRestTemplate();

    private static RestTemplate creerRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(60000); // réveil Render gratuit
        return new RestTemplate(factory);
    }

    // Traduire utilisateurId (du token) → chauffeur.id réel
    public Long getChauffeurId(Long utilisateurId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Api-Key", internalApiKey);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Long> response = restTemplate.exchange(
                authServiceUrl + "/api/chauffeurs/by-utilisateur/" + utilisateurId,
                HttpMethod.GET,
                entity,
                Long.class
        );
        return response.getBody();
    }

    // Journal admin (best effort, ne bloque jamais l'action)
    public void journaliser(String emailAdmin, String action, String entite, Long entiteId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Api-Key", internalApiKey);
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            java.util.Map<String, Object> body = new java.util.HashMap<>();
            body.put("email", emailAdmin);
            body.put("action", action);
            body.put("entite", entite);
            body.put("entiteId", entiteId);
            restTemplate.exchange(authServiceUrl + "/api/interne/journal", HttpMethod.POST,
                    new HttpEntity<>(body, headers), Void.class);
        } catch (Exception ignore) {
        }
    }
}

package paiement_service.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class AuthServiceClient {

    @Value("${app.auth-service.url}")
    private String authServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    // Traduire utilisateurId (du token) → chauffeur.id réel
    public Long getChauffeurId(Long utilisateurId, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Long> response = restTemplate.exchange(
                authServiceUrl + "/api/chauffeurs/by-utilisateur/" + utilisateurId,
                HttpMethod.GET,
                entity,
                Long.class
        );
        return response.getBody();
    }
}
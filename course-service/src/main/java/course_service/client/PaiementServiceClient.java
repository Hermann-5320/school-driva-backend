package course_service.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class PaiementServiceClient {

    @Value("${app.paiement-service.url}")
    private String paiementServiceUrl;

    @Value("${app.internal-api-key}")
    private String internalApiKey;

    private final RestTemplate restTemplate = creerRestTemplate();

    private static RestTemplate creerRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(60000); // réveil Render gratuit
        return new RestTemplate(factory);
    }

    // La commission est calculée UNE seule fois, ici (paramètre COMMISSION_POURCENTAGE),
    // et transmise telle quelle à paiement-service.
    public void deduireCommission(Long chauffeurId, BigDecimal montantCourse, BigDecimal commission, Long courseId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Api-Key", internalApiKey);

        // HashMap (et non Map.of) : tolère une commission nulle
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("chauffeurId", chauffeurId);
        body.put("montantCourse", montantCourse);
        body.put("commission", commission);
        body.put("courseId", courseId);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        restTemplate.postForObject(
                paiementServiceUrl + "/api/paiements/interne/commission",
                entity,
                String.class
        );
    }
    // Le seuil vient du paramètre admin SOLDE_MINIMUM_CHAUFFEUR
    public boolean soldeSuffisant(Long chauffeurId, BigDecimal seuil) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Api-Key", internalApiKey);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        Map reponse = restTemplate.exchange(
                paiementServiceUrl + "/api/paiements/verifier-solde/" + chauffeurId + "?seuil=" + seuil.toPlainString(),
                org.springframework.http.HttpMethod.GET,
                entity,
                Map.class
        ).getBody();

        return reponse != null && Boolean.TRUE.equals(reponse.get("suffisant"));
    }
}
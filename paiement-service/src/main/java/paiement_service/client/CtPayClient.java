package paiement_service.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class CtPayClient {

    @Value("${app.ctpay.token}")
    private String token;

    @Value("${app.ctpay.base-url}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    // Initier un paiement (recharge chauffeur)
    public Map initierPaiement(String phoneNumber, String amount, String motif,
                               String transactionUniqueId, String operatorKey,
                               String callbackUrl) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Token", token);
        headers.set("callbackUrl", callbackUrl);

        Map<String, Object> body = Map.of(
                "amount", Integer.parseInt(amount),
                "motif", motif,
                "operatorKey", operatorKey,
                "sourcePhoneNumber", phoneNumber,
                "transactionUniqueID", transactionUniqueId
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        return restTemplate.postForObject(
                baseUrl + "/payments/pay/withPhone",
                entity,
                Map.class
        );
    }

    // Vérifier le statut d'un paiement
    public Map verifierStatut(String processCode) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Token", token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        return restTemplate.exchange(
                baseUrl + "/api/processes/" + processCode,
                org.springframework.http.HttpMethod.GET,
                entity,
                Map.class
        ).getBody();
    }
}
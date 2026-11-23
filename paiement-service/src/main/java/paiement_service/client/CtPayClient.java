package paiement_service.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Client CT PAY (documentation « CT PAY : Paiement API », v1 du 05/10/2025).
 * Base : https://gateway-prod.connecttechnology.io/ct-payment
 *  - POST /payments/pay/withPhone   (en-têtes Token + callbackUrl)
 *  - GET  /api/processes/{processCode}
 * Réponse : { code, status: SUCCESS|ERROR, message, data: {...} }
 */
@Component
public class CtPayClient {

    @Value("${app.ctpay.token}")
    private String token;

    @Value("${app.ctpay.base-url}")
    private String baseUrl;

    private final RestTemplate restTemplate = creerRestTemplate();

    private static RestTemplate creerRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(30000);
        return new RestTemplate(factory);
    }

    private HttpHeaders enTetes() {
        if (token == null || token.isBlank()) {
            throw new RuntimeException("Paiement Mobile Money non configuré (CTPAY_TOKEN manquant)");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Token", token);
        return headers;
    }

    /**
     * Initie un paiement. Le numéro doit être au format local à 9 chiffres (6XXXXXXXX), sans 237.
     * @return le bloc "data" (processCode, processStatus...) ; exception si CT PAY refuse.
     */
    public Map<String, Object> initierPaiement(String telephoneInternational, long montant, String motif,
                                               String transactionUniqueId, String operatorKey, String callbackUrl) {
        HttpHeaders headers = enTetes();
        headers.set("callbackUrl", callbackUrl);
        Map<String, Object> body = Map.of(
                "amount", montant,
                "motif", motif,
                "operatorKey", operatorKey,
                "sourcePhoneNumber", telephoneInternational,
                "transactionUniqueID", transactionUniqueId
        );
        return extraireData(appeler(baseUrl + "/payments/pay/withPhone", HttpMethod.POST, new HttpEntity<>(body, headers)));
    }

    /** Statut réel d'une transaction. @return le bloc "data" (processStatus, finalised...). */
    public Map<String, Object> verifierStatut(String processCode) {
        return extraireData(appeler(baseUrl + "/api/processes/" + processCode, HttpMethod.GET, new HttpEntity<>(enTetes())));
    }

    private Map<String, Object> appeler(String url, HttpMethod methode, HttpEntity<?> requete) {
        try {
            return restTemplate.exchange(url, methode, requete,
                    new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();
        } catch (HttpStatusCodeException e) {
            // CT PAY renvoie un JSON { message } même en erreur : on remonte un message lisible
            String detail = e.getResponseBodyAsString();
            throw new RuntimeException("CT PAY a refusé la demande"
                    + (detail != null && detail.contains("\"message\"") ? " : " + extraireMessage(detail) : ""));
        } catch (RestClientException e) {
            throw new RuntimeException("CT PAY est injoignable pour le moment, réessayez plus tard");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extraireData(Map<String, Object> reponse) {
        if (reponse == null) {
            throw new RuntimeException("Réponse vide de CT PAY");
        }
        if (!"SUCCESS".equalsIgnoreCase(String.valueOf(reponse.get("status"))) || !(reponse.get("data") instanceof Map)) {
            throw new RuntimeException("CT PAY : " + reponse.getOrDefault("message", "opération refusée"));
        }
        return (Map<String, Object>) reponse.get("data");
    }

    private static String extraireMessage(String json) {
        int i = json.indexOf("\"message\"");
        int debut = json.indexOf('"', json.indexOf(':', i) + 1);
        int fin = json.indexOf('"', debut + 1);
        return (debut > 0 && fin > debut) ? json.substring(debut + 1, fin) : "erreur";
    }
}

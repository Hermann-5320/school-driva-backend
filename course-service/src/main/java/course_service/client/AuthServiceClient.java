package course_service.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Appels vers auth-service. Les endpoints internes sont protégés par la clé
 * X-Internal-Api-Key ; le token utilisateur est transmis quand l'appel est
 * fait au nom de l'utilisateur (ex : liste des chauffeurs en ligne).
 */
@Component
public class AuthServiceClient {

    @Value("${app.auth-service.url}")
    private String authServiceUrl;

    @Value("${app.internal-api-key}")
    private String internalApiKey;

    private final RestTemplate restTemplate = creerRestTemplate();

    private static RestTemplate creerRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        // Long timeout : sur Render gratuit, auth-service peut mettre ~1 min à se réveiller
        factory.setReadTimeout(60000);
        return new RestTemplate(factory);
    }

    private HttpHeaders enTetesInternes() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Api-Key", internalApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    // ── Traduction utilisateurId (token) → id métier ───────
    public Long getPassagerId(Long utilisateurId) {
        return restTemplate.exchange(
                authServiceUrl + "/api/passagers/by-utilisateur/" + utilisateurId,
                HttpMethod.GET, new HttpEntity<>(enTetesInternes()), Long.class).getBody();
    }

    public Long getChauffeurId(Long utilisateurId) {
        return restTemplate.exchange(
                authServiceUrl + "/api/chauffeurs/by-utilisateur/" + utilisateurId,
                HttpMethod.GET, new HttpEntity<>(enTetesInternes()), Long.class).getBody();
    }

    // ── Infos d'un passager (nom, prénom, téléphone) ──────
    public Map<String, Object> getPassagerPublic(Long passagerId) {
        return restTemplate.exchange(
                authServiceUrl + "/api/passagers/" + passagerId + "/public",
                HttpMethod.GET, new HttpEntity<>(enTetesInternes()),
                new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();
    }

    // ── Chauffeurs en ligne d'une ville (au nom du passager) ─
    public List<Map<String, Object>> getChauffeursDisponibles(Long villeId, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", token);
        return restTemplate.exchange(
                authServiceUrl + "/api/chauffeurs/en-ligne?villeId=" + villeId,
                HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}).getBody();
    }

    // ── Mise à jour des stats chauffeur (interne) ──────────
    public void mettreAJourStatsChauffeur(Long chauffeurId, Double noteMoyenne) {
        envoyerStats(chauffeurId, Map.of("noteMoyenne", noteMoyenne != null ? noteMoyenne : 0.0));
    }

    public void incrementerCoursesChauffeur(Long chauffeurId) {
        envoyerStats(chauffeurId, Map.of("incrementerCourses", true));
    }

    public void ajouterKilometres(Long chauffeurId, BigDecimal km) {
        envoyerStats(chauffeurId, Map.of("kmAAjouter", km));
    }

    // ── Résumé chauffeur (type de véhicule, ville, véhicule) ─
    // Mis en cache 5 min : le tableau de bord chauffeur interroge toutes les 5 s.
    private record EntreeCache(Map<String, Object> valeur, long expireA) {}
    private final java.util.concurrent.ConcurrentHashMap<Long, EntreeCache> cacheResume =
            new java.util.concurrent.ConcurrentHashMap<>();

    // ── Villes actives avec leurs coordonnées (endpoint public /api/villes) ─
    // Mises en cache 10 min : elles changent rarement.
    private volatile List<Map<String, Object>> cacheVilles;
    private volatile long cacheVillesExpireA;

    public List<Map<String, Object>> getVillesActives() {
        if (cacheVilles != null && cacheVillesExpireA > System.currentTimeMillis()) return cacheVilles;
        List<Map<String, Object>> villes = restTemplate.exchange(
                authServiceUrl + "/api/villes", HttpMethod.GET, new HttpEntity<>(enTetesInternes()),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}).getBody();
        cacheVilles = villes == null ? List.of() : villes;
        cacheVillesExpireA = System.currentTimeMillis() + 600_000;
        return cacheVilles;
    }

    public Map<String, Object> getResumeChauffeur(Long chauffeurId) {
        EntreeCache e = cacheResume.get(chauffeurId);
        if (e != null && e.expireA() > System.currentTimeMillis()) return e.valeur();
        Map<String, Object> resume = restTemplate.exchange(
                authServiceUrl + "/api/interne/chauffeurs/" + chauffeurId + "/resume",
                HttpMethod.GET, new HttpEntity<>(enTetesInternes()),
                new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();
        if (resume != null) {
            cacheResume.put(chauffeurId, new EntreeCache(resume, System.currentTimeMillis() + 300_000));
        }
        return resume;
    }

    // ── Nombre de chauffeurs en ligne d'une ville, par type de véhicule ─
    public long compterChauffeursEnLigne(Long villeId, String typeVehicule) {
        Map<String, Object> r = restTemplate.exchange(
                authServiceUrl + "/api/interne/chauffeurs/en-ligne/nombre?villeId=" + villeId + "&typeVehicule=" + typeVehicule,
                HttpMethod.GET, new HttpEntity<>(enTetesInternes()),
                new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();
        return r != null && r.get("nombre") instanceof Number n ? n.longValue() : 0;
    }

    // ── Journal admin (best effort) ───────────────────────
    public void journaliser(String emailAdmin, String action, String entite, Long entiteId) {
        try {
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("email", emailAdmin);
            body.put("action", action);
            body.put("entite", entite);
            body.put("entiteId", entiteId);
            restTemplate.exchange(authServiceUrl + "/api/interne/journal", HttpMethod.POST,
                    new HttpEntity<>(body, enTetesInternes()), Void.class);
        } catch (Exception ignore) {
            // Le journal ne doit jamais bloquer l'action
        }
    }

    private void envoyerStats(Long chauffeurId, Map<String, Object> body) {
        restTemplate.exchange(
                authServiceUrl + "/api/chauffeurs/" + chauffeurId + "/stats",
                HttpMethod.PUT, new HttpEntity<>(body, enTetesInternes()), Void.class);
    }
}

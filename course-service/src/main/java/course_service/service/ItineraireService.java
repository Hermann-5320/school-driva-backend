package course_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Distance, durée et tracé RÉELS par la route, via OpenRouteService (gratuit :
 * 2 000 itinéraires/jour avec une clé). Sans clé, ou si le service ne répond
 * pas, repli automatique : distance à vol d'oiseau × 1,4 (détour moyen en ville).
 */
@Service
public class ItineraireService {

    private static final Logger log = LoggerFactory.getLogger(ItineraireService.class);

    // Coefficient de détour du mode dégradé (à recalibrer avec les vraies courses)
    static final double COEFFICIENT_DETOUR = 1.4;
    // Vitesse moyenne urbaine du mode dégradé, pour estimer une durée
    static final double VITESSE_MOYENNE_KMH = 20;
    private static final int TAILLE_MAX_CACHE = 1000;

    /** Itinéraire calculé. trace = liste de [lat, lng] prête pour Leaflet. */
    public record Itineraire(double distanceKm, int dureeMin, List<double[]> trace, String source) {}

    private final RestTemplate restTemplate;
    // Les trajets se répètent beaucoup (campus ↔ quartiers) : on économise le quota
    private final Map<String, Itineraire> cache = new ConcurrentHashMap<>();

    @Value("${app.ors.api-key:}")
    private String cleApi;

    @Value("${app.ors.base-url:https://api.openrouteservice.org}")
    private String urlBase;

    // Paramètre admin ITINERAIRE_PREFERENCE (absent dans les tests unitaires)
    private ParametreService parametreService;

    @Autowired(required = false)
    void setParametreService(ParametreService parametreService) {
        this.parametreService = parametreService;
    }

    /**
     * Critère de calcul ORS, réglable par l'admin :
     * 1 = plus court (défaut : le prix étant au km, c'est le plus juste pour le passager),
     * 2 = plus rapide, 3 = recommandé (privilégie les grands axes).
     */
    String preference() {
        int code = parametreService == null ? 1
                : parametreService.getValeurDecimal("ITINERAIRE_PREFERENCE", "1").intValue();
        return switch (code) {
            case 2 -> "fastest";
            case 3 -> "recommended";
            default -> "shortest";
        };
    }

    @Autowired
    public ItineraireService() {
        this(creerRestTemplate());
    }

    // Utilisé par les tests pour simuler les réponses d'OpenRouteService
    ItineraireService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    private static RestTemplate creerRestTemplate() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(3000);
        f.setReadTimeout(6000);
        return new RestTemplate(f);
    }

    public Itineraire calculer(double departLat, double departLng, double arriveeLat, double arriveeLng) {
        // Clé de cache : coordonnées arrondies à ~11 m
        String preference = preference();
        String cle = String.format(Locale.ROOT, "%s:%.4f,%.4f>%.4f,%.4f", preference, departLat, departLng, arriveeLat, arriveeLng);
        Itineraire enCache = cache.get(cle);
        if (enCache != null) return enCache;

        Itineraire resultat = null;
        if (cleApi != null && !cleApi.isBlank()) {
            try {
                resultat = appelerOpenRouteService(departLat, departLng, arriveeLat, arriveeLng, preference);
            } catch (Exception e) {
                log.warn("OpenRouteService indisponible, estimation utilisée : {}", e.getMessage());
            }
        }
        if (resultat == null) {
            return estimation(departLat, departLng, arriveeLat, arriveeLng); // pas mis en cache : on retentera ORS
        }
        if (cache.size() >= TAILLE_MAX_CACHE) cache.clear();
        cache.put(cle, resultat);
        return resultat;
    }

    @SuppressWarnings("unchecked")
    private Itineraire appelerOpenRouteService(double dLat, double dLng, double aLat, double aLng, String preference) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", cleApi);
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Attention : ORS attend [longitude, latitude]
        Map<String, Object> corps = Map.of(
                "coordinates", List.of(List.of(dLng, dLat), List.of(aLng, aLat)),
                "preference", preference);

        Map<String, Object> reponse = restTemplate.postForObject(
                urlBase.replaceAll("/+$", "") + "/v2/directions/driving-car/geojson",
                new HttpEntity<>(corps, headers), Map.class);

        List<Map<String, Object>> features = (List<Map<String, Object>>) reponse.get("features");
        Map<String, Object> feature = features.get(0);
        Map<String, Object> resume = (Map<String, Object>) ((Map<String, Object>) feature.get("properties")).get("summary");
        double distanceKm = ((Number) resume.getOrDefault("distance", 0)).doubleValue() / 1000.0;
        double dureeS = ((Number) resume.getOrDefault("duration", 0)).doubleValue();

        List<List<Number>> coordonnees = (List<List<Number>>) ((Map<String, Object>) feature.get("geometry")).get("coordinates");
        List<double[]> trace = new ArrayList<>(coordonnees.size());
        for (List<Number> c : coordonnees) {
            trace.add(new double[]{c.get(1).doubleValue(), c.get(0).doubleValue()}); // → [lat, lng]
        }
        return new Itineraire(distanceKm, (int) Math.max(1, Math.round(dureeS / 60.0)), trace, "OPENROUTESERVICE");
    }

    /** Mode dégradé : vol d'oiseau × 1,4 et tracé en ligne droite. */
    Itineraire estimation(double dLat, double dLng, double aLat, double aLng) {
        double distanceKm = CourseService.calculerDistanceKm(dLat, dLng, aLat, aLng) * COEFFICIENT_DETOUR;
        int dureeMin = (int) Math.max(1, Math.round(distanceKm / VITESSE_MOYENNE_KMH * 60));
        return new Itineraire(distanceKm, dureeMin, List.of(new double[]{dLat, dLng}, new double[]{aLat, aLng}), "ESTIMATION");
    }
}

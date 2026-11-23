package course_service.service;

import course_service.dto.SuggestionAdresseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GeocodageService {

    private static final Logger log = LoggerFactory.getLogger(GeocodageService.class);

    @Value("${app.serpapi.key}")
    private String serpApiKey;

    private final RestTemplate restTemplate = creerRestTemplate();

    private static RestTemplate creerRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(8000);
        return new RestTemplate(factory);
    }

    private static final String CENTRE_YAOUNDE = "@3.848,11.502,13z";

    // Cache mémoire simple : évite de refacturer un appel SerpAPI quand
    // plusieurs passagers (ou le même, en retapant) recherchent la même
    // adresse. Essentiel en MVP vu le quota mensuel limité de l'API.
    private static final Duration DUREE_CACHE = Duration.ofHours(24);
    private final Map<String, EntreeCache> cache = new ConcurrentHashMap<>();

    private record EntreeCache(List<SuggestionAdresseDTO> resultats, Instant expiration) {
        boolean estValide() {
            return Instant.now().isBefore(expiration);
        }
    }

    public List<SuggestionAdresseDTO> rechercherAdresses(String recherche) {

        String cle = recherche.trim().toLowerCase();

        EntreeCache entree = cache.get(cle);
        if (entree != null && entree.estValide()) {
            log.debug("Cache HIT pour '{}'", cle);
            return entree.resultats();
        }

        String url = UriComponentsBuilder
                .fromUriString("https://serpapi.com/search.json")
                .queryParam("engine", "google_maps_autocomplete")
                .queryParam("q", recherche)
                .queryParam("ll", CENTRE_YAOUNDE)
                .queryParam("api_key", serpApiKey)
                .toUriString();

        List<SuggestionAdresseDTO> resultats = new ArrayList<>();

        try {
            Map response = restTemplate.getForObject(url, Map.class);

            if (response != null && response.containsKey("suggestions")) {
                List<Map<String, Object>> suggestions = (List<Map<String, Object>>) response.get("suggestions");

                for (Map<String, Object> suggestion : suggestions) {
                    if ("place".equals(suggestion.get("type"))) {
                        resultats.add(new SuggestionAdresseDTO(
                                (String) suggestion.get("value"),
                                (String) suggestion.getOrDefault("subtext", ""),
                                suggestion.get("latitude") != null ? ((Number) suggestion.get("latitude")).doubleValue() : null,
                                suggestion.get("longitude") != null ? ((Number) suggestion.get("longitude")).doubleValue() : null
                        ));
                    }
                }
            }

            // On met en cache même une liste vide : évite de retaper la même
            // requête ratée à SerpAPI en boucle (ex: quota déjà épuisé).
            cache.put(cle, new EntreeCache(resultats, Instant.now().plus(DUREE_CACHE)));

        } catch (RestClientException e) {
            log.warn("Échec appel SerpAPI pour '{}': {}", cle, e.getMessage());
            // Pas de mise en cache d'un échec réseau ponctuel : on veut
            // réessayer au prochain appel, pas figer l'erreur 24h.
        }

        return resultats;
    }
}
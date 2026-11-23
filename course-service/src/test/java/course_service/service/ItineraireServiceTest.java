package course_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Itinéraire routier : réponse OpenRouteService simulée, et repli si indisponible. */
class ItineraireServiceTest {

    // Bastos → Université de Yaoundé I
    private static final double D_LAT = 3.8800, D_LNG = 11.5100, A_LAT = 3.8589, A_LNG = 11.4995;

    private RestTemplate restTemplate;
    private MockRestServiceServer serveurOrs;
    private ItineraireService service;

    @BeforeEach
    void preparer() {
        restTemplate = new RestTemplate();
        serveurOrs = MockRestServiceServer.bindTo(restTemplate).build();
        service = new ItineraireService(restTemplate);
        ReflectionTestUtils.setField(service, "urlBase", "https://ors.test");
        ReflectionTestUtils.setField(service, "cleApi", "cle-de-test");
    }

    @Test
    void laDistanceRoutiereEtLeTraceViennentDOpenRouteService() {
        serveurOrs.expect(requestTo("https://ors.test/v2/directions/driving-car/geojson"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "cle-de-test"))
                // ORS attend [longitude, latitude]
                .andExpect(jsonPath("$.coordinates[0][0]").value(D_LNG))
                // Par défaut : le trajet le plus court (prix au km)
                .andExpect(jsonPath("$.preference").value("shortest"))
                .andRespond(withSuccess("""
                        {"type":"FeatureCollection","features":[{"type":"Feature",
                          "properties":{"summary":{"distance":3850.0,"duration":540.0}},
                          "geometry":{"type":"LineString","coordinates":[[11.5100,3.8800],[11.5050,3.8700],[11.4995,3.8589]]}}]}
                        """, MediaType.APPLICATION_JSON));

        ItineraireService.Itineraire it = service.calculer(D_LAT, D_LNG, A_LAT, A_LNG);

        assertThat(it.source()).isEqualTo("OPENROUTESERVICE");
        assertThat(it.distanceKm()).isCloseTo(3.85, within(0.001));
        assertThat(it.dureeMin()).isEqualTo(9);
        assertThat(it.trace()).hasSize(3);
        assertThat(it.trace().get(0)).containsExactly(D_LAT, D_LNG); // reconverti en [lat, lng] pour Leaflet

        // Deuxième demande identique : servie par le cache, pas de nouvel appel
        service.calculer(D_LAT, D_LNG, A_LAT, A_LNG);
        serveurOrs.verify();
    }

    @Test
    void siOpenRouteServiceEchoueOnEstimeAvecLeCoefficientDeDetour() {
        serveurOrs.expect(requestTo("https://ors.test/v2/directions/driving-car/geojson"))
                .andRespond(withServerError());

        ItineraireService.Itineraire it = service.calculer(D_LAT, D_LNG, A_LAT, A_LNG);

        double volDOiseau = CourseService.calculerDistanceKm(D_LAT, D_LNG, A_LAT, A_LNG);
        assertThat(it.source()).isEqualTo("ESTIMATION");
        assertThat(it.distanceKm()).isCloseTo(volDOiseau * 1.4, within(0.0001));
        assertThat(it.trace()).hasSize(2);
    }

    @Test
    void sansCleAucunAppelNestFait() {
        ReflectionTestUtils.setField(service, "cleApi", "");
        ItineraireService.Itineraire it = service.calculer(D_LAT, D_LNG, A_LAT, A_LNG);
        assertThat(it.source()).isEqualTo("ESTIMATION");
        serveurOrs.verify(); // aucune requête attendue, aucune reçue
    }
}

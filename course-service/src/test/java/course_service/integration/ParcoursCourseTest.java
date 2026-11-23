package course_service.integration;

import com.jayway.jsonpath.JsonPath;
import course_service.client.AuthServiceClient;
import course_service.client.PaiementServiceClient;
import course_service.entity.ParametreSysteme;
import course_service.repository.CourseRepository;
import course_service.repository.NotationRepository;
import course_service.repository.PaiementRepository;
import course_service.repository.ParametreSystemeRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Parcours critiques du service des courses, de bout en bout (HTTP → sécurité
 * → service → base H2). Les appels vers auth-service et paiement-service sont simulés.
 */
@SpringBootTest
@ActiveProfiles("test")
class ParcoursCourseTest {

    // Utilisateurs (userId dans le JWT) → identifiants métier simulés
    private static final long PASSAGER_A = 100L, PASSAGER_B = 101L;
    private static final long CHAUFFEUR_1 = 200L, CHAUFFEUR_2 = 201L;

    @Autowired WebApplicationContext contexte;
    @Autowired CourseRepository courseRepository;
    @Autowired PaiementRepository paiementRepository;
    @Autowired NotationRepository notationRepository;
    @Autowired ParametreSystemeRepository parametreRepository;

    @MockitoBean AuthServiceClient authServiceClient;
    @MockitoBean PaiementServiceClient paiementServiceClient;

    @Value("${app.jwt.secret}") String secret;

    MockMvc mvc;

    @BeforeEach
    void preparer() {
        mvc = MockMvcBuilders.webAppContextSetup(contexte).apply(springSecurity()).build();
        notationRepository.deleteAll();
        paiementRepository.deleteAll();
        courseRepository.deleteAll();
        parametreRepository.deleteAll();
        for (var p : Map.of("COMMISSION_POURCENTAGE", "15", "TARIF_VOITURE_KM", "500", "TARIF_MOTO_KM", "300",
                "PRIX_MINIMUM", "500", "SOLDE_MINIMUM_CHAUFFEUR", "500", "DELAI_RECHERCHE_MIN", "10",
                "DISTANCE_MAX_KM", "60", "TIMER_COURSE", "15").entrySet()) {
            parametreRepository.save(new ParametreSysteme(p.getKey(), p.getValue(), null, java.time.LocalDateTime.now()));
        }
        when(authServiceClient.getPassagerId(10L)).thenReturn(PASSAGER_A);
        when(authServiceClient.getPassagerId(11L)).thenReturn(PASSAGER_B);
        when(authServiceClient.getChauffeurId(20L)).thenReturn(CHAUFFEUR_1);
        when(authServiceClient.getChauffeurId(21L)).thenReturn(CHAUFFEUR_2);
        when(authServiceClient.getResumeChauffeur(anyLong()))
                .thenReturn(Map.of("typeVehicule", "VOITURE", "villeId", 1, "prenom", "Paul"));
        when(paiementServiceClient.soldeSuffisant(anyLong(), any())).thenReturn(true);
        // Par défaut, un chauffeur de chaque type est en ligne
        when(authServiceClient.compterChauffeursEnLigne(anyLong(), anyString())).thenReturn(1L);
        // Seule ville desservie dans les tests : Yaoundé (id 1)
        when(authServiceClient.getVillesActives())
                .thenReturn(java.util.List.of(Map.of("id", 1, "nom", "Yaoundé", "lat", 3.8480, "lng", 11.5021)));
    }

    private String jeton(String email, String role, long userId) {
        return "Bearer " + Jwts.builder()
                .subject(email).claim("role", role).claim("userId", userId).claim("type", "ACCES")
                .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private String passagerA() { return jeton("a@test.cm", "PASSAGER", 10L); }
    private String passagerB() { return jeton("b@test.cm", "PASSAGER", 11L); }
    private String chauffeur1() { return jeton("c1@test.cm", "CHAUFFEUR", 20L); }
    private String chauffeur2() { return jeton("c2@test.cm", "CHAUFFEUR", 21L); }
    private String admin() { return jeton("admin@test.cm", "ADMIN", 1L); }

    private long creerCourse(String jetonPassager) throws Exception {
        String reponse = mvc.perform(post("/api/courses").header("Authorization", jetonPassager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"typeVehicule":"VOITURE","departAdresse":"Bastos","departLat":3.8800,"departLng":11.5100,
                                 "arriveeAdresse":"Université de Yaoundé I","arriveeLat":3.8589,"arriveeLng":11.4995,"villeId":1}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(reponse, "$.id")).longValue();
    }

    private void passerStatut(long courseId, String statut) throws Exception {
        mvc.perform(put("/api/courses/" + courseId + "/statut").header("Authorization", chauffeur1())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"statut\":\"" + statut + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void uneCourseNePeutEtreAccepteeQueParUnSeulChauffeur() throws Exception {
        long id = creerCourse(passagerA());

        mvc.perform(put("/api/courses/" + id + "/accepter").header("Authorization", chauffeur1()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ACCEPTEE"))
                .andExpect(jsonPath("$.chauffeurId").value(CHAUFFEUR_1));

        mvc.perform(put("/api/courses/" + id + "/accepter").header("Authorization", chauffeur2()))
                .andExpect(status().isBadRequest());

        assertThat(courseRepository.findById(id).orElseThrow().getChauffeurId()).isEqualTo(CHAUFFEUR_1);
    }

    @Test
    void unPassagerNePeutPasConsulterNiAnnulerLaCourseDUnAutre() throws Exception {
        long id = creerCourse(passagerA());

        mvc.perform(get("/api/courses/" + id).header("Authorization", passagerB()))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/courses/" + id + "/annuler").header("Authorization", passagerB()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/courses/" + id).header("Authorization", passagerA()))
                .andExpect(status().isOk());
    }

    @Test
    void unPassagerNePeutAvoirQuUneSeuleCourseActive() throws Exception {
        creerCourse(passagerA());
        mvc.perform(post("/api/courses").header("Authorization", passagerA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"typeVehicule":"MOTO","departAdresse":"Mvan","departLat":3.83,"departLng":11.52,
                                 "arriveeAdresse":"Centre","arriveeLat":3.86,"arriveeLng":11.51,"villeId":1}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uneCourseSansCoordonneesEstRefusee() throws Exception {
        mvc.perform(post("/api/courses").header("Authorization", passagerA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"typeVehicule\":\"VOITURE\",\"departAdresse\":\"A\",\"arriveeAdresse\":\"B\",\"villeId\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void laCommissionUtiliseLeTauxDefiniParLAdmin() throws Exception {
        mvc.perform(put("/api/admin/parametres/COMMISSION_POURCENTAGE").header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"valeur\":\"20\"}"))
                .andExpect(status().isOk());

        long id = creerCourse(passagerA());
        mvc.perform(put("/api/courses/" + id + "/accepter").header("Authorization", chauffeur1()))
                .andExpect(status().isOk());
        passerStatut(id, "EN_ROUTE");
        passerStatut(id, "ARRIVEE");
        passerStatut(id, "DEMARREE");
        passerStatut(id, "TERMINEE");

        String paiement = mvc.perform(post("/api/courses/" + id + "/paiement").header("Authorization", passagerA())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"LIQUIDE\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        BigDecimal montant = new BigDecimal(JsonPath.read(paiement, "$.montant").toString());
        BigDecimal commission = new BigDecimal(JsonPath.read(paiement, "$.commission").toString());
        BigDecimal attendue = montant.multiply(new BigDecimal("20")).divide(new BigDecimal("100"), 0, RoundingMode.HALF_UP);
        assertThat(commission).isEqualByComparingTo(attendue);

        mvc.perform(put("/api/courses/" + id + "/paiement/confirmer").header("Authorization", chauffeur1()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("CONFIRME"));

        // paiement-service reçoit exactement la commission calculée avec le taux admin
        verify(paiementServiceClient).deduireCommission(eq(CHAUFFEUR_1), argThat(m -> m.compareTo(montant) == 0),
                argThat(c -> c.compareTo(attendue) == 0), eq(id));
    }

    @Test
    void unTauxDeCommissionHorsLimitesEstRefuse() throws Exception {
        mvc.perform(put("/api/admin/parametres/COMMISSION_POURCENTAGE").header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"valeur\":\"500\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/parametres/COMMISSION_POURCENTAGE").header("Authorization", passagerA())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"valeur\":\"1\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void leChauffeurPeutConfirmerSansDeclarationDuPassager() throws Exception {
        long id = creerCourse(passagerA());
        mvc.perform(put("/api/courses/" + id + "/accepter").header("Authorization", chauffeur1())).andExpect(status().isOk());
        passerStatut(id, "EN_ROUTE");
        passerStatut(id, "ARRIVEE");
        passerStatut(id, "DEMARREE");
        passerStatut(id, "TERMINEE");

        mvc.perform(put("/api/courses/" + id + "/paiement/confirmer").header("Authorization", chauffeur1())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"MTN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("MTN"));
    }

    @Test
    void lAnnulationParLeChauffeurRemetLaCourseEnRecherche() throws Exception {
        long id = creerCourse(passagerA());
        mvc.perform(put("/api/courses/" + id + "/accepter").header("Authorization", chauffeur1())).andExpect(status().isOk());

        mvc.perform(put("/api/courses/" + id + "/annuler-chauffeur").header("Authorization", chauffeur1()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"));

        // Un autre chauffeur peut maintenant la prendre
        mvc.perform(put("/api/courses/" + id + "/accepter").header("Authorization", chauffeur2()))
                .andExpect(status().isOk());
    }

    @Test
    void leLienDePartageEstPublicLimiteEtExpireALaFin() throws Exception {
        long id = creerCourse(passagerA());

        // Seul le passager propriétaire peut générer le lien
        mvc.perform(post("/api/courses/" + id + "/partage").header("Authorization", passagerB()))
                .andExpect(status().isForbidden());

        String reponse = mvc.perform(post("/api/courses/" + id + "/partage").header("Authorization", passagerA()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(reponse, "$.token");

        // Accessible sans compte, sans données sensibles
        mvc.perform(get("/api/courses/partage/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arriveeAdresse").value("Université de Yaoundé I"))
                .andExpect(jsonPath("$.passagerId").doesNotExist())
                .andExpect(jsonPath("$.telephone").doesNotExist());

        // Le jeton n'apparaît jamais dans le JSON de la course
        mvc.perform(get("/api/courses/" + id).header("Authorization", passagerA()))
                .andExpect(jsonPath("$.partageToken").doesNotExist());

        // Course annulée → le lien ne montre plus que le statut
        mvc.perform(put("/api/courses/" + id + "/annuler").header("Authorization", passagerA()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/courses/partage/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ANNULEE"))
                .andExpect(jsonPath("$.arriveeAdresse").doesNotExist());

        // Jeton inventé → refusé
        mvc.perform(get("/api/courses/partage/jeton-invente-0123456789abcdef"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void laPositionDuChauffeurEstVisibleParLePassager() throws Exception {
        long id = creerCourse(passagerA());
        mvc.perform(put("/api/courses/" + id + "/accepter").header("Authorization", chauffeur1())).andExpect(status().isOk());

        // Un autre chauffeur ne peut pas envoyer de position pour cette course
        mvc.perform(put("/api/courses/" + id + "/position").header("Authorization", chauffeur2())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lat\":3.87,\"lng\":11.50}"))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/courses/" + id + "/position").header("Authorization", chauffeur1())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lat\":3.87,\"lng\":11.50}"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/courses/" + id).header("Authorization", passagerA()))
                .andExpect(jsonPath("$.chauffeurLat").value(3.87));
    }

    @Test
    void lesEndpointsAdminSontInterditsAuxAutresRoles() throws Exception {
        mvc.perform(get("/api/courses/admin").header("Authorization", passagerA()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/courses/admin").header("Authorization", admin()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/courses/admin"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void laVilleDeLaCourseDependDuPointDeDepartPasDuProfil() throws Exception {
        // Le client envoie villeId 99 : ignoré, la course est rattachée à Yaoundé (départ à Bastos)
        String reponse = mvc.perform(post("/api/courses").header("Authorization", passagerA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"typeVehicule":"VOITURE","departAdresse":"Bastos","departLat":3.8800,"departLng":11.5100,
                                 "arriveeAdresse":"Mvan","arriveeLat":3.8300,"arriveeLng":11.5200,"villeId":99}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(((Number) JsonPath.read(reponse, "$.villeId")).longValue()).isEqualTo(1L);
    }

    @Test
    void uneReservationHorsZoneDesservieEstRefusee() throws Exception {
        // Départ à Ambam (~165 km de Yaoundé) : aucune ville desservie dans le rayon
        mvc.perform(post("/api/courses").header("Authorization", passagerA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"typeVehicule":"MOTO","departAdresse":"Ambam centre","departLat":2.3833,"departLng":11.2833,
                                 "arriveeAdresse":"ESTLC Ambam","arriveeLat":2.3900,"arriveeLng":11.2700,"villeId":1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("pas encore disponible")));
        assertThat(courseRepository.findAll()).isEmpty();
    }

    @Test
    void laZoneEstVerifieeAvantLaReservation() throws Exception {
        mvc.perform(get("/api/courses/zone").param("lat", "3.87").param("lng", "11.51")
                        .header("Authorization", passagerA()))
                .andExpect(jsonPath("$.desservie").value(true))
                .andExpect(jsonPath("$.villeId").value(1));
        mvc.perform(get("/api/courses/zone").param("lat", "2.3833").param("lng", "11.2833")
                        .header("Authorization", passagerA()))
                .andExpect(jsonPath("$.desservie").value(false));
    }

    @Test
    void lItineraireDUneCourseSuitLesDroitsDeLaCourse() throws Exception {
        long id = creerCourse(passagerA());

        mvc.perform(get("/api/courses/" + id + "/itineraire").header("Authorization", passagerB()))
                .andExpect(status().isForbidden());

        // Sans clé ORS dans les tests : estimation (ligne droite × 1,4), tracé de 2 points
        mvc.perform(get("/api/courses/" + id + "/itineraire").header("Authorization", passagerA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("ESTIMATION"))
                .andExpect(jsonPath("$.trace.length()").value(2));
    }

    @Test
    void uneReservationEstRefuseeSansChauffeurDuBonTypeEnLigne() throws Exception {
        when(authServiceClient.compterChauffeursEnLigne(1L, "MOTO")).thenReturn(0L);

        mvc.perform(post("/api/courses").header("Authorization", passagerA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"typeVehicule":"MOTO","departAdresse":"Bastos","departLat":3.8800,"departLng":11.5100,
                                 "arriveeAdresse":"Mvan","arriveeLat":3.8300,"arriveeLng":11.5200}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Aucun chauffeur moto")));
        assertThat(courseRepository.findAll()).isEmpty();

        // La voiture, elle, reste réservable
        creerCourse(passagerA());
    }

    @Test
    void leTempsRestantDeRechercheEstFourniParLeServeur() throws Exception {
        long id = creerCourse(passagerA());
        String r = mvc.perform(get("/api/courses/" + id + "/recherche").header("Authorization", passagerA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"))
                .andReturn().getResponse().getContentAsString();
        long secondes = ((Number) JsonPath.read(r, "$.secondesRestantes")).longValue();
        assertThat(secondes).isBetween(590L, 600L); // délai par défaut : 10 min

        mvc.perform(get("/api/courses/" + id + "/recherche").header("Authorization", passagerB()))
                .andExpect(status().isForbidden());
    }
}

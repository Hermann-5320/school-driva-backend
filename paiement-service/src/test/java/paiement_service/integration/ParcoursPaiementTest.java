package paiement_service.integration;

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
import paiement_service.client.AuthServiceClient;
import paiement_service.client.CtPayClient;
import paiement_service.entity.Portefeuille;
import paiement_service.entity.Recharge;
import paiement_service.repository.PortefeuilleRepository;
import paiement_service.repository.RechargeRepository;
import paiement_service.repository.TransactionRepository;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Parcours critiques de l'argent : recharges, commissions, endpoints internes. */
@SpringBootTest
@ActiveProfiles("test")
class ParcoursPaiementTest {

    private static final long CHAUFFEUR = 5L;

    @Autowired WebApplicationContext contexte;
    @Autowired PortefeuilleRepository portefeuilleRepository;
    @Autowired RechargeRepository rechargeRepository;
    @Autowired TransactionRepository transactionRepository;

    @MockitoBean CtPayClient ctPayClient;
    @MockitoBean AuthServiceClient authServiceClient;

    @Value("${app.internal-api-key}") String cleInterne;
    @Value("${app.jwt.secret}") String secret;

    MockMvc mvc;

    @BeforeEach
    void preparer() {
        mvc = MockMvcBuilders.webAppContextSetup(contexte).apply(springSecurity()).build();
        transactionRepository.deleteAll();
        rechargeRepository.deleteAll();
        portefeuilleRepository.deleteAll();
    }

    private String jeton(String role, long userId) {
        return "Bearer " + Jwts.builder()
                .subject(role.toLowerCase() + "@test.cm").claim("role", role).claim("userId", userId)
                .claim("type", "ACCES").issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
    }

    private Recharge rechargeEnAttente(String payToken, String montant) {
        Recharge r = new Recharge();
        r.setChauffeurId(CHAUFFEUR);
        r.setMontant(new BigDecimal(montant));
        r.setOperateur("MTN");
        r.setStatut("EN_ATTENTE");
        r.setPayToken(payToken);
        return rechargeRepository.save(r);
    }

    private void portefeuille(String solde) {
        Portefeuille p = new Portefeuille();
        p.setChauffeurId(CHAUFFEUR);
        p.setSolde(new BigDecimal(solde));
        portefeuilleRepository.save(p);
    }

    private BigDecimal solde() {
        return portefeuilleRepository.findByChauffeurId(CHAUFFEUR).orElseThrow().getSolde();
    }

    @Test
    void unWebhookRejoueNeCreditePasDeuxFois() throws Exception {
        rechargeEnAttente("PT-1", "2000");
        when(ctPayClient.verifierStatut("PT-1"))
                .thenReturn(Map.of("processStatus", "SUCCESS", "finalised", true));

        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/paiements/webhooks/ctpay").param("id", "PT-1")).andExpect(status().isOk());
        }

        assertThat(solde()).isEqualByComparingTo("2000");
        assertThat(transactionRepository.findAll()).hasSize(1);
    }

    @Test
    void leWebhookNeCroitPasLePayloadMaisCtPay() throws Exception {
        rechargeEnAttente("PT-2", "5000");
        // Le payload dit "succès" mais CT Pay répond "échec" : rien n'est crédité
        when(ctPayClient.verifierStatut("PT-2"))
                .thenReturn(Map.of("processStatus", "FAILED", "finalised", true));

        mvc.perform(post("/api/paiements/webhooks/ctpay").param("id", "PT-2").param("status", "SUCCESS"))
                .andExpect(status().isOk());

        assertThat(rechargeRepository.findByPayToken("PT-2").orElseThrow().getStatut()).isEqualTo("REJETEE");
        assertThat(portefeuilleRepository.findByChauffeurId(CHAUFFEUR)).isEmpty();
    }

    @Test
    void uneTransactionEncoreEnAttenteChezCtPayNeChangeRien() throws Exception {
        rechargeEnAttente("PT-5", "3000");
        when(ctPayClient.verifierStatut("PT-5"))
                .thenReturn(Map.of("processCode", "PT-5", "processStatus", "PENDING", "finalised", false));

        mvc.perform(post("/api/paiements/webhooks/ctpay").param("id", "PT-5").param("status", "SUCCESS"))
                .andExpect(status().isOk());

        assertThat(rechargeRepository.findByPayToken("PT-5").orElseThrow().getStatut()).isEqualTo("EN_ATTENTE");
    }

    @Test
    void leWebhookAccepteAussiNotreReferenceDeTransaction() throws Exception {
        Recharge r = rechargeEnAttente("PT-6", "1500");
        r.setReferenceTransaction("SD_REF_6");
        rechargeRepository.save(r);
        when(ctPayClient.verifierStatut("PT-6"))
                .thenReturn(Map.of("processCode", "PT-6", "processStatus", "SUCCESS", "finalised", true));

        mvc.perform(post("/api/paiements/webhooks/ctpay").param("id", "SD_REF_6")).andExpect(status().isOk());

        assertThat(solde()).isEqualByComparingTo("1500");
    }

    @Test
    void uneRechargeDejaValideeNePeutPasEtreReconfirmeeParLAdmin() throws Exception {
        Recharge r = rechargeEnAttente("PT-3", "1000");
        mvc.perform(put("/api/paiements/admin/recharges/" + r.getId() + "/confirmer")
                        .header("Authorization", jeton("ADMIN", 1L)))
                .andExpect(status().isOk());
        mvc.perform(put("/api/paiements/admin/recharges/" + r.getId() + "/confirmer")
                        .header("Authorization", jeton("ADMIN", 1L)))
                .andExpect(status().isBadRequest());

        assertThat(solde()).isEqualByComparingTo("1000");
    }

    @Test
    void laCommissionDUneCourseNEstDeduiteQuUneFois() throws Exception {
        portefeuille("5000");
        String corps = "{\"chauffeurId\":5,\"montantCourse\":2000,\"commission\":300,\"courseId\":42}";

        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/paiements/interne/commission").header("X-Internal-Api-Key", cleInterne)
                            .contentType(MediaType.APPLICATION_JSON).content(corps))
                    .andExpect(status().isOk());
        }

        // 300 = commission transmise par course-service (taux admin), pas un taux codé en dur
        assertThat(solde()).isEqualByComparingTo("4700");
    }

    @Test
    void lesEndpointsInternesExigentLaCle() throws Exception {
        String corps = "{\"chauffeurId\":5,\"montantCourse\":2000,\"commission\":300,\"courseId\":43}";
        mvc.perform(post("/api/paiements/interne/commission")
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/paiements/interne/commission").header("X-Internal-Api-Key", "mauvaise-cle")
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isForbidden());
        // Même un JWT admin valide ne suffit pas
        mvc.perform(get("/api/paiements/verifier-solde/5").header("Authorization", jeton("ADMIN", 1L)))
                .andExpect(status().isForbidden());
    }

    @Test
    void leSeuilDeSoldeVientDuParametreTransmis() throws Exception {
        portefeuille("1000");
        mvc.perform(get("/api/paiements/verifier-solde/5").param("seuil", "1500")
                        .header("X-Internal-Api-Key", cleInterne))
                .andExpect(jsonPath("$.suffisant").value(false));
        mvc.perform(get("/api/paiements/verifier-solde/5").param("seuil", "500")
                        .header("X-Internal-Api-Key", cleInterne))
                .andExpect(jsonPath("$.suffisant").value(true));
    }

    @Test
    void unChauffeurNePeutPasVerifierLaRechargeDUnAutre() throws Exception {
        Recharge r = rechargeEnAttente("PT-4", "1000");
        when(authServiceClient.getChauffeurId(99L)).thenReturn(77L); // autre chauffeur
        mvc.perform(post("/api/paiements/recharges/" + r.getId() + "/verifier")
                        .header("Authorization", jeton("CHAUFFEUR", 99L)))
                .andExpect(status().isForbidden());
    }
}

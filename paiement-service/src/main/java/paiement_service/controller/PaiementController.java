package paiement_service.controller;

import paiement_service.client.AuthServiceClient;
import paiement_service.dto.CommissionDTO;
import paiement_service.dto.DemandeRechargeDTO;
import paiement_service.dto.PortefeuilleResponseDTO;
import paiement_service.entity.Recharge;
import paiement_service.entity.Transaction;
import paiement_service.security.JwtService;
import paiement_service.service.PaiementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import paiement_service.dto.StatsFinancieresDTO;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/paiements")
@RequiredArgsConstructor
public class PaiementController {

    private final PaiementService paiementService;
    private final JwtService jwtService;
    private final AuthServiceClient authServiceClient;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PaiementController.class);

    // false dans les tests (traitement synchrone pour pouvoir vérifier le résultat)
    @org.springframework.beans.factory.annotation.Value("${app.ctpay.webhook-async:true}")
    private boolean webhookAsynchrone;

    // ── CHAUFFEUR ──────────────────────────────────────────

    // Voir son portefeuille
    @GetMapping("/portefeuille")
    @PreAuthorize("hasRole('CHAUFFEUR')")
    public ResponseEntity<PortefeuilleResponseDTO> getPortefeuille(
            @RequestHeader("Authorization") String token) {
        Long chauffeurId = extraireChauffeurId(token);
        return ResponseEntity.ok(paiementService.getPortefeuille(chauffeurId));
    }

    // Historique des transactions
    @GetMapping("/transactions")
    @PreAuthorize("hasRole('CHAUFFEUR')")
    public ResponseEntity<List<Transaction>> getTransactions(
            @RequestHeader("Authorization") String token) {
        Long chauffeurId = extraireChauffeurId(token);
        return ResponseEntity.ok(paiementService.getTransactions(chauffeurId));
    }

    // Demander une recharge
    @PostMapping("/recharges")
    @PreAuthorize("hasRole('CHAUFFEUR')")
    public ResponseEntity<Recharge> demanderRecharge(
            @Valid @RequestBody DemandeRechargeDTO dto,
            @RequestHeader("Authorization") String token) {
        Long chauffeurId = extraireChauffeurId(token);
        return ResponseEntity.ok(paiementService.demanderRecharge(chauffeurId, dto));
    }

    // Revérifier auprès de CT Pay une recharge restée en attente
    @PostMapping("/recharges/{id}/verifier")
    @PreAuthorize("hasRole('CHAUFFEUR')")
    public ResponseEntity<Recharge> verifierRecharge(@PathVariable Long id,
                                                     @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(paiementService.verifierRechargeChauffeur(id, extraireChauffeurId(token)));
    }

    // Historique des recharges
    @GetMapping("/recharges")
    @PreAuthorize("hasRole('CHAUFFEUR')")
    public ResponseEntity<List<Recharge>> getRecharges(
            @RequestHeader("Authorization") String token) {
        Long chauffeurId = extraireChauffeurId(token);
        return ResponseEntity.ok(paiementService.getRecharges(chauffeurId));
    }

    // ── INTERNE (appelé par Course Service) ────────────────

    @PostMapping("/interne/commission")
    public ResponseEntity<String> deduireCommission(@Valid @RequestBody CommissionDTO dto) {
        paiementService.deduireCommission(dto);
        return ResponseEntity.ok("Commission déduite avec succès");
    }

    // ── WEBHOOK AANGARAAPAY ─────────────────────────────────

    @PostMapping("/webhooks/ctpay")
    public ResponseEntity<String> webhookCtPay(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String id,
            @RequestBody(required = false) Map<String, Object> body) {

        String finalId = id;

        if (finalId == null && body != null) {
            finalId = (String) body.get("id");
            if (finalId == null) {
                finalId = (String) body.get("paytoken");
            }
        }

        if (finalId == null) {
            return ResponseEntity.badRequest().body("ID de transaction manquant");
        }

        // Doc CT PAY : répondre 200 immédiatement et traiter la notification en
        // arrière-plan. Le traitement interroge CT PAY (source de vérité, la
        // notification n'étant pas signée) et est idempotent : un webhook
        // rejoué ne crédite jamais deux fois.
        final String identifiant = finalId;
        Runnable traitement = () -> {
            try {
                paiementService.verifierEtConfirmerRecharge(identifiant);
            } catch (RuntimeException e) {
                log.warn("Notification CT PAY {} non traitée : {}", identifiant, e.getMessage());
            }
        };
        if (webhookAsynchrone) {
            java.util.concurrent.CompletableFuture.runAsync(traitement);
        } else {
            traitement.run();
        }
        return ResponseEntity.ok("OK");
    }

    // INTERNE (clé X-Internal-Api-Key) : le seuil vient du paramètre admin
    @GetMapping("/verifier-solde/{chauffeurId}")
    public ResponseEntity<Map<String, Boolean>> verifierSolde(@PathVariable Long chauffeurId,
                                                              @RequestParam(required = false) java.math.BigDecimal seuil) {
        PortefeuilleResponseDTO portefeuille = paiementService.getPortefeuille(chauffeurId, seuil);
        boolean suffisant = !portefeuille.getSoldeInsuffisant();
        return ResponseEntity.ok(Map.of("suffisant", suffisant));
    }

    // ── UTILITAIRE ──────────────────────────────────────────

    private Long extraireChauffeurId(String token) {
        String jwt = token.substring(7);
        Long utilisateurId = jwtService.extraireUserId(jwt);
        return authServiceClient.getChauffeurId(utilisateurId);
    }
    // ── ADMIN ──────────────────────────────────────────────

    @GetMapping("/admin/recharges")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Recharge>> getToutesRecharges(
            @RequestParam(required = false) String statut) {
        if (statut != null) {
            return ResponseEntity.ok(paiementService.getRechargesParStatut(statut));
        }
        return ResponseEntity.ok(paiementService.getToutesRecharges());
    }

    @GetMapping("/admin/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StatsFinancieresDTO> getStatsFinancieres() {
        return ResponseEntity.ok(paiementService.getStatsFinancieres());
    }

    @PutMapping("/admin/recharges/{id}/confirmer")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> confirmerRechargeManuellement(@PathVariable Long id,
                                                                            @RequestHeader("Authorization") String token) {
        paiementService.confirmerRechargeManuellement(id);
        authServiceClient.journaliser(jwtService.extraireEmail(token.substring(7)),
                "Validation manuelle de la recharge #" + id, "RECHARGE", id);
        return ResponseEntity.ok(Map.of("message", "Recharge confirmée"));
    }

    @PutMapping("/admin/recharges/{id}/rejeter")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> rejeterRecharge(@PathVariable Long id,
                                                               @RequestBody(required = false) Map<String, String> body,
                                                               @RequestHeader("Authorization") String token) {
        String raison = body != null ? body.get("raison") : null;
        if (raison != null && raison.length() > 500) raison = raison.substring(0, 500);
        paiementService.rejeterRecharge(id, raison);
        authServiceClient.journaliser(jwtService.extraireEmail(token.substring(7)),
                "Rejet de la recharge #" + id, "RECHARGE", id);
        return ResponseEntity.ok(Map.of("message", "Recharge rejetée"));
    }

    @GetMapping("/admin/portefeuilles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<paiement_service.entity.Portefeuille>> portefeuilles() {
        return ResponseEntity.ok(paiementService.getTousPortefeuilles());
    }

    @GetMapping("/admin/transactions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Transaction>> transactions() {
        return ResponseEntity.ok(paiementService.getDernieresTransactions());
    }

}
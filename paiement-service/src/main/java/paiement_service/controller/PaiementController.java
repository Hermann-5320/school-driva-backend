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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/paiements")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PaiementController {

    private final PaiementService paiementService;
    private final JwtService jwtService;
    private final AuthServiceClient authServiceClient;

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

        String finalStatus = status;
        String finalId = id;

        if (finalStatus == null && body != null) {
            finalStatus = (String) body.get("status");
            finalId = (String) body.get("id");
        }

        boolean succes = "SUCCESS".equalsIgnoreCase(finalStatus);
        paiementService.confirmerRecharge(finalId, succes);

        return ResponseEntity.ok("OK");
    }
    @GetMapping("/verifier-solde/{chauffeurId}")
    public ResponseEntity<Map<String, Boolean>> verifierSolde(@PathVariable Long chauffeurId) {
        PortefeuilleResponseDTO portefeuille = paiementService.getPortefeuille(chauffeurId);
        boolean suffisant = !portefeuille.getSoldeInsuffisant();
        return ResponseEntity.ok(Map.of("suffisant", suffisant));
    }

    // ── UTILITAIRE ──────────────────────────────────────────

    private Long extraireChauffeurId(String token) {
        String jwt = token.substring(7);
        Long utilisateurId = jwtService.extraireUserId(jwt);
        return authServiceClient.getChauffeurId(utilisateurId, token);
    }

}
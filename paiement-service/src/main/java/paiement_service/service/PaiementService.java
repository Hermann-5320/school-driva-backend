package paiement_service.service;

import paiement_service.dto.CommissionDTO;
import paiement_service.dto.DemandeRechargeDTO;
import paiement_service.dto.PortefeuilleResponseDTO;
import paiement_service.entity.Portefeuille;
import paiement_service.entity.Recharge;
import paiement_service.entity.Transaction;
import paiement_service.repository.PortefeuilleRepository;
import paiement_service.repository.RechargeRepository;
import paiement_service.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import paiement_service.client.CtPayClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaiementService {

    private final PortefeuilleRepository portefeuilleRepository;
    private final TransactionRepository transactionRepository;
    private final RechargeRepository rechargeRepository;
    private final CtPayClient ctPayClient;

    // Seuil minimum en dessous duquel le chauffeur ne peut plus accepter de courses
    private static final BigDecimal SEUIL_MINIMUM = new BigDecimal("500");

    // Taux de commission — sera rendu configurable comme dans Course Service
    private static final BigDecimal TAUX_COMMISSION = new BigDecimal("0.15");

    // ── OBTENIR OU CRÉER LE PORTEFEUILLE ──────────────────
    @Transactional
    public Portefeuille getOuCreerPortefeuille(Long chauffeurId) {
        return portefeuilleRepository.findByChauffeurId(chauffeurId)
                .orElseGet(() -> {
                    Portefeuille nouveau = new Portefeuille();
                    nouveau.setChauffeurId(chauffeurId);
                    nouveau.setSolde(BigDecimal.ZERO);
                    return portefeuilleRepository.save(nouveau);
                });
    }

    // ── VOIR LE PORTEFEUILLE ──────────────────────────────
    public PortefeuilleResponseDTO getPortefeuille(Long chauffeurId) {
        Portefeuille portefeuille = getOuCreerPortefeuille(chauffeurId);
        boolean insuffisant = portefeuille.getSolde().compareTo(SEUIL_MINIMUM) < 0;
        return new PortefeuilleResponseDTO(chauffeurId, portefeuille.getSolde(), insuffisant);
    }

    // ── DEDUIRE LA COMMISSION APRES UNE COURSE ────────────
    @Transactional
    public void deduireCommission(CommissionDTO dto) {
        Portefeuille portefeuille = getOuCreerPortefeuille(dto.getChauffeurId());

        BigDecimal commission = dto.getMontantCourse()
                .multiply(TAUX_COMMISSION)
                .setScale(0, RoundingMode.HALF_UP);

        // On ne bloque pas si le solde devient insuffisant — juste un avertissement logique
        // Le chauffeur sera bloqué au moment d'accepter une NOUVELLE course (vérifié côté Course Service)
        BigDecimal nouveauSolde = portefeuille.getSolde().subtract(commission);
        portefeuille.setSolde(nouveauSolde.max(BigDecimal.ZERO)); // jamais négatif en base
        portefeuille.setUpdatedAt(java.time.LocalDateTime.now());
        portefeuilleRepository.save(portefeuille);

        Transaction transaction = new Transaction();
        transaction.setPortefeuilleId(portefeuille.getId());
        transaction.setType("COMMISSION");
        transaction.setMontant(commission);
        transaction.setCourseId(dto.getCourseId());
        transactionRepository.save(transaction);
    }

    // ── DEMANDER UNE RECHARGE ─────────────────────────────
    @Transactional
    public Recharge demanderRecharge(Long chauffeurId, DemandeRechargeDTO dto) {

        String transactionId = "RECHARGE_" + UUID.randomUUID().toString().substring(0, 12);
        String operatorKey = dto.getOperateur().equalsIgnoreCase("MTN") ? "MOMO" : "OM";
        String callbackUrl = "http://paiement-service:8083/api/paiements/webhooks/ctpay";

        // Retirer le préfixe 237 s'il est présent
        String telephone = dto.getTelephone().startsWith("237")
                ? dto.getTelephone().substring(3)
                : dto.getTelephone();

        Map reponse = ctPayClient.initierPaiement(
                telephone,
                dto.getMontant().toBigInteger().toString(),
                "Recharge School Driva",
                transactionId,
                operatorKey,
                callbackUrl
        );

        Recharge recharge = new Recharge();
        recharge.setChauffeurId(chauffeurId);
        recharge.setMontant(dto.getMontant());
        recharge.setOperateur(dto.getOperateur().toUpperCase());
        recharge.setStatut("EN_ATTENTE");
        recharge.setReferenceTransaction(transactionId);

        if (reponse != null && reponse.containsKey("data")) {
            Map data = (Map) reponse.get("data");
            recharge.setPayToken((String) data.get("processCode"));
        }

        return rechargeRepository.save(recharge);
    }

    // ── CONFIRMER UNE RECHARGE (appelé par le webhook) ────
    @Transactional
    public void confirmerRecharge(String payToken, boolean succes) {
        Recharge recharge = rechargeRepository.findByPayToken(payToken)
                .orElseThrow(() -> new RuntimeException("Recharge introuvable pour ce token"));

        if (succes) {
            recharge.setStatut("VALIDEE");
            recharge.setConfirmedAt(java.time.LocalDateTime.now());
            rechargeRepository.save(recharge);

            Portefeuille portefeuille = getOuCreerPortefeuille(recharge.getChauffeurId());
            portefeuille.setSolde(portefeuille.getSolde().add(recharge.getMontant()));
            portefeuille.setUpdatedAt(java.time.LocalDateTime.now());
            portefeuilleRepository.save(portefeuille);

            Transaction transaction = new Transaction();
            transaction.setPortefeuilleId(portefeuille.getId());
            transaction.setType("RECHARGE");
            transaction.setMontant(recharge.getMontant());
            transaction.setRechargeId(recharge.getId());
            transactionRepository.save(transaction);
        } else {
            recharge.setStatut("REJETEE");
            recharge.setRaisonRejet("Paiement échoué ou refusé par l'opérateur");
            rechargeRepository.save(recharge);
        }
    }

    // ── HISTORIQUE TRANSACTIONS ────────────────────────────
    public List<Transaction> getTransactions(Long chauffeurId) {
        Portefeuille portefeuille = getOuCreerPortefeuille(chauffeurId);
        return transactionRepository.findByPortefeuilleIdOrderByCreatedAtDesc(portefeuille.getId());
    }

    // ── HISTORIQUE RECHARGES ───────────────────────────────
    public List<Recharge> getRecharges(Long chauffeurId) {
        return rechargeRepository.findByChauffeurIdOrderByCreatedAtDesc(chauffeurId);
    }
}
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import paiement_service.dto.StatsFinancieresDTO;

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

    // Taux de secours uniquement : normalement la commission arrive déjà
    // calculée par course-service (paramètre COMMISSION_POURCENTAGE).
    private static final BigDecimal TAUX_COMMISSION = new BigDecimal("0.15");

    // URL publique du gateway (ex : https://schooldriva-gateway.onrender.com),
    // pour que CT Pay puisse appeler le webhook depuis Internet.
    @Value("${app.public-base-url}")
    private String publicBaseUrl;

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
        return getPortefeuille(chauffeurId, SEUIL_MINIMUM);
    }

    // Seuil fourni par course-service (paramètre admin SOLDE_MINIMUM_CHAUFFEUR)
    public PortefeuilleResponseDTO getPortefeuille(Long chauffeurId, BigDecimal seuil) {
        Portefeuille portefeuille = getOuCreerPortefeuille(chauffeurId);
        boolean insuffisant = portefeuille.getSolde().compareTo(seuil == null ? SEUIL_MINIMUM : seuil) < 0;
        return new PortefeuilleResponseDTO(chauffeurId, portefeuille.getSolde(), insuffisant);
    }

    // ── DEDUIRE LA COMMISSION APRES UNE COURSE ────────────
    @Transactional
    public void deduireCommission(CommissionDTO dto) {
        // Idempotent : une course ne génère qu'une seule commission
        if (dto.getCourseId() != null
                && transactionRepository.existsByCourseIdAndType(dto.getCourseId(), "COMMISSION")) {
            return;
        }
        Portefeuille portefeuille = getOuCreerPortefeuille(dto.getChauffeurId());

        BigDecimal commission = dto.getCommission() != null
                ? dto.getCommission()
                : dto.getMontantCourse().multiply(TAUX_COMMISSION).setScale(0, RoundingMode.HALF_UP);

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
        // Une seule recharge en attente à la fois (évite les doubles prompts USSD)
        boolean dejaEnCours = rechargeRepository.findByChauffeurIdOrderByCreatedAtDesc(chauffeurId).stream()
                .anyMatch(r -> "EN_ATTENTE".equals(r.getStatut())
                        && r.getCreatedAt().isAfter(java.time.LocalDateTime.now().minusMinutes(10)));
        if (dejaEnCours) {
            throw new RuntimeException("Une recharge est déjà en cours. Validez-la sur votre téléphone ou attendez quelques minutes.");
        }

        String transactionId = "RECHARGE_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        // Clés opérateur CT Pay (doc : /api/operator/getAll) : MOMO = MTN, OM = Orange
        String operatorKey = dto.getOperateur().equalsIgnoreCase("MTN") ? "MOMO" : "OM";
        String callbackUrl = publicBaseUrl.replaceAll("/+$", "") + "/api/paiements/webhooks/ctpay";

        // CT Pay attend le numéro local à 9 chiffres (6XXXXXXXX), SANS 237
        // (testé en réel : la documentation indique 237..., mais l'API le refuse)
        String telephone = dto.getTelephone().replaceAll("\\s", "").replaceFirst("^\\+?237", "");

        // Le client lève une exception lisible si CT PAY refuse (status ERROR)
        Map<String, Object> data = ctPayClient.initierPaiement(
                telephone,
                dto.getMontant().longValue(),
                "Recharge School Driva",
                transactionId,
                operatorKey,
                callbackUrl
        );
        if (data.get("processCode") == null) {
            throw new RuntimeException("CT PAY n'a pas renvoyé de code de transaction");
        }

        Recharge recharge = new Recharge();
        recharge.setChauffeurId(chauffeurId);
        recharge.setMontant(dto.getMontant());
        recharge.setOperateur(dto.getOperateur().toUpperCase());
        recharge.setStatut("EN_ATTENTE");
        recharge.setReferenceTransaction(transactionId);
        recharge.setTelephone(telephone);
        // processCode : identifiant CT Pay, renvoyé ensuite dans le webhook (paramètre "id")
        recharge.setPayToken(String.valueOf(data.get("processCode")));

        return rechargeRepository.save(recharge);
    }

    // ── CONFIRMER UNE RECHARGE ─────────────────────────────
    // Idempotent : seule une recharge EN_ATTENTE peut changer d'état.
    // La ligne est verrouillée pendant la transaction.
    @Transactional
    public void confirmerRecharge(String payToken, boolean succes) {
        // Le webhook peut transmettre le processCode CT PAY ou notre transactionUniqueID
        Recharge recharge = rechargeRepository.findByPayTokenPourMaj(payToken)
                .or(() -> rechargeRepository.findByReferenceTransactionPourMaj(payToken))
                .orElseThrow(() -> new RuntimeException("Recharge introuvable pour ce token"));
        appliquerResultat(recharge, succes);
    }

    private void appliquerResultat(Recharge recharge, boolean succes) {
        if (!"EN_ATTENTE".equals(recharge.getStatut())) {
            return; // déjà traitée : on ne crédite jamais deux fois
        }

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

    // ── REJETER UNE RECHARGE (ADMIN) ──────────────────────
    @Transactional
    public void rejeterRecharge(Long rechargeId, String raison) {
        Recharge recharge = rechargeRepository.findByIdPourMaj(rechargeId)
                .orElseThrow(() -> new RuntimeException("Recharge introuvable"));
        if (!"EN_ATTENTE".equals(recharge.getStatut())) {
            throw new RuntimeException("Cette recharge a déjà été traitée (" + recharge.getStatut() + ")");
        }
        recharge.setStatut("REJETEE");
        recharge.setRaisonRejet(raison == null || raison.isBlank() ? "Rejetée par l'administrateur" : raison);
        rechargeRepository.save(recharge);
    }

    // ── VERIFIER UNE RECHARGE (CHAUFFEUR) ─────────────────
    // Utile si le webhook CT Pay n'a pas pu joindre le serveur : on interroge
    // CT Pay directement. Le chauffeur ne peut vérifier que ses recharges.
    @Transactional
    public Recharge verifierRechargeChauffeur(Long rechargeId, Long chauffeurId) {
        Recharge recharge = rechargeRepository.findById(rechargeId)
                .orElseThrow(() -> new RuntimeException("Recharge introuvable"));
        if (!recharge.getChauffeurId().equals(chauffeurId)) {
            throw new org.springframework.security.access.AccessDeniedException("Cette recharge ne vous appartient pas");
        }
        if ("EN_ATTENTE".equals(recharge.getStatut()) && recharge.getPayToken() != null) {
            verifierEtConfirmerRecharge(recharge.getPayToken());
        }
        return rechargeRepository.findById(rechargeId).orElse(recharge);
    }

    // ── ADMIN — LISTES ─────────────────────────────────────
    public List<Portefeuille> getTousPortefeuilles() {
        return portefeuilleRepository.findAll();
    }

    public List<Transaction> getDernieresTransactions() {
        return transactionRepository.findTop200ByOrderByCreatedAtDesc();
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
    // ── ADMIN — TOUTES LES RECHARGES ──────────────────────
    public List<Recharge> getToutesRecharges() {
        return rechargeRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<Recharge> getRechargesParStatut(String statut) {
        return rechargeRepository.findByStatut(statut);
    }

    // ── ADMIN — STATS FINANCIÈRES ─────────────────────────
    public StatsFinancieresDTO getStatsFinancieres() {
        BigDecimal totalCommissions = transactionRepository.getTotalCommissions();
        BigDecimal totalRecharges = transactionRepository.getTotalRecharges();
        Long rechargesEnAttente = rechargeRepository.countByStatut("EN_ATTENTE");
        BigDecimal soldeCumule = portefeuilleRepository.getSoldeCumule();

        return new StatsFinancieresDTO(totalCommissions, totalRecharges, rechargesEnAttente, soldeCumule);
    }

    // ── ADMIN — CONFIRMER MANUELLEMENT UNE RECHARGE ───────
// Utile en attendant le webhook réel (ngrok), ou en cas de problème de notification
    @Transactional
    public void confirmerRechargeManuellement(Long rechargeId) {
        Recharge recharge = rechargeRepository.findByIdPourMaj(rechargeId)
                .orElseThrow(() -> new RuntimeException("Recharge introuvable"));
        if (!"EN_ATTENTE".equals(recharge.getStatut())) {
            throw new RuntimeException("Cette recharge a déjà été traitée (" + recharge.getStatut() + ")");
        }
        appliquerResultat(recharge, true);
    }
    // verification et confirmation de la recharge
    @Transactional
    public void verifierEtConfirmerRecharge(String identifiant) {
        // L'identifiant reçu peut être notre référence : on retrouve le processCode
        String processCode = rechargeRepository.findByReferenceTransaction(identifiant)
                .map(Recharge::getPayToken).orElse(identifiant);

        // Source de vérité : la réponse de CT PAY, jamais le contenu du webhook
        // (la documentation ne prévoit aucune signature des notifications).
        Map<String, Object> data = ctPayClient.verifierStatut(processCode);
        String statut = String.valueOf(data.get("processStatus")).toUpperCase();
        boolean finalise = !Boolean.FALSE.equals(data.get("finalised"));

        switch (statut) {
            case "SUCCESS" -> confirmerRecharge(processCode, true);
            case "FAILED", "CANCELLED", "ERROR" -> confirmerRecharge(processCode, false);
            default -> { if (!finalise || "PENDING".equals(statut)) return; } // encore en attente
        }
    }


}
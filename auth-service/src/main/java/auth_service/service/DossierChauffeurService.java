package auth_service.service;

import auth_service.entity.Chauffeur;
import auth_service.entity.Document;
import auth_service.repository.ChauffeurRepository;
import auth_service.repository.UtilisateurRepository;
import auth_service.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Permet à un chauffeur dont le dossier n'est pas encore validé de le
 * compléter plus tard, via un lien personnel (48 h) reçu par email.
 * Le lien n'autorise QUE l'envoi de documents pour son dossier ; il ne
 * permet pas de se connecter.
 */
@Service
@RequiredArgsConstructor
public class DossierChauffeurService {

    private static final long DELAI_ENTRE_DEUX_LIENS_MS = 60_000;

    private final UtilisateurRepository utilisateurRepository;
    private final ChauffeurRepository chauffeurRepository;
    private final JwtService jwtService;
    private final EmailService emailService;

    // Anti-spam : un lien par minute et par email (instance unique sur Render)
    private final Map<String, Long> derniersEnvois = new ConcurrentHashMap<>();

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    /** Lien personnel vers la page « Compléter mon dossier ». */
    public String lienDossier(Long chauffeurId) {
        String token = jwtService.genererTokenUpload(chauffeurId);
        return frontendUrl.replaceAll("/+$", "") + "/completer-dossier?token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    /**
     * Envoie le lien si l'email correspond à un chauffeur dont le dossier est
     * en attente. Ne révèle jamais si le compte existe (le contrôleur renvoie
     * toujours le même message).
     */
    public void envoyerLienSiEligible(String email) {
        if (email == null || email.isBlank()) return;
        String normalise = email.trim().toLowerCase();

        long maintenant = System.currentTimeMillis();
        Long dernier = derniersEnvois.get(normalise);
        if (dernier != null && maintenant - dernier < DELAI_ENTRE_DEUX_LIENS_MS) return;

        utilisateurRepository.findByEmail(normalise)
                .flatMap(u -> chauffeurRepository.findByUtilisateurId(u.getId()))
                .filter(c -> c.getStatut() == Chauffeur.Statut.EN_ATTENTE)
                .ifPresent(c -> {
                    derniersEnvois.put(normalise, maintenant);
                    emailService.envoyerLienDossier(normalise, c.getPrenom(), lienDossier(c.getId()));
                });
    }

    /** Prévient le chauffeur qu'un document a été rejeté, avec le moyen de le renvoyer. */
    public void notifierRejet(Document document) {
        Chauffeur c = document.getChauffeur();
        String email = c.getUtilisateur().getEmail();
        // Dossier pas encore validé → lien ; compte actif → il passe par son espace « Documents »
        String lien = c.getStatut() == Chauffeur.Statut.EN_ATTENTE ? lienDossier(c.getId()) : null;
        emailService.envoyerDocumentRejete(email, c.getPrenom(), document.getType(), document.getRaisonRejet(), lien);
    }
}

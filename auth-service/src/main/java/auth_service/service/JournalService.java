package auth_service.service;

import auth_service.entity.JournalAdmin;
import auth_service.repository.AdminRepository;
import auth_service.repository.JournalAdminRepository;
import auth_service.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** Journal des actions d'administration (qui a fait quoi, quand). */
@Service
@RequiredArgsConstructor
public class JournalService {

    private static final Logger log = LoggerFactory.getLogger(JournalService.class);

    private final JournalAdminRepository journalRepository;
    private final AdminRepository adminRepository;
    private final UtilisateurRepository utilisateurRepository;

    /** Journalise une action de l'admin actuellement connecté. */
    public void log(String action, String entite, Long entiteId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return;
        logPourEmail(auth.getName(), action, entite, entiteId);
    }

    /** Journalise une action pour un admin identifié par son email (appels internes). */
    public void logPourEmail(String email, String action, String entite, Long entiteId) {
        try {
            utilisateurRepository.findByEmail(email)
                    .flatMap(u -> adminRepository.findByUtilisateurId(u.getId()))
                    .ifPresent(admin -> {
                        JournalAdmin entree = new JournalAdmin();
                        entree.setAdminId(admin.getId());
                        entree.setAction(action.length() > 500 ? action.substring(0, 500) : action);
                        entree.setEntite(entite);
                        entree.setEntiteId(entiteId);
                        journalRepository.save(entree);
                    });
        } catch (Exception e) {
            // Le journal ne doit jamais bloquer l'action elle-même
            log.warn("Journalisation impossible : {}", e.getMessage());
        }
    }
}

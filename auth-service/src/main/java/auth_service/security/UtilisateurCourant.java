package auth_service.security;

import auth_service.entity.Utilisateur;
import auth_service.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Accès à l'utilisateur authentifié (déjà vérifié par JwtFilter). */
@Component
@RequiredArgsConstructor
public class UtilisateurCourant {

    private final UtilisateurRepository utilisateurRepository;

    public Utilisateur get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null
                || "anonymousUser".equals(auth.getPrincipal())) {
            throw new AccessDeniedException("Authentification requise");
        }
        return utilisateurRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new AccessDeniedException("Utilisateur introuvable"));
    }

    public boolean estConnecte() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal());
    }
}

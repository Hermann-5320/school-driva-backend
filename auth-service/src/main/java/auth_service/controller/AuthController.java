package auth_service.controller;

import auth_service.dto.*;
import auth_service.security.UtilisateurCourant;
import auth_service.service.AuthService;
import auth_service.service.JournalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UtilisateurCourant utilisateurCourant;
    private final JournalService journalService;

    @PostMapping("/connexion")
    public ResponseEntity<TokenDTO> connexion(@Valid @RequestBody ConnexionDTO dto) {
        return ResponseEntity.ok(authService.connecter(dto));
    }

    @PostMapping("/passager/inscription")
    public ResponseEntity<Map<String, String>> inscrirePassager(@Valid @RequestBody InscriptionPassagerDTO dto) {
        authService.inscrirePassager(dto);
        return ResponseEntity.ok(Map.of("message", "Compte créé avec succès. Vous pouvez vous connecter."));
    }

    // Retourne l'id du chauffeur + un jeton d'upload (2 h) limité à SES documents
    @PostMapping("/chauffeur/pre-inscription")
    public ResponseEntity<Map<String, Object>> preInscrireChauffeur(@Valid @RequestBody PreInscriptionChauffeurDTO dto) {
        return ResponseEntity.ok(authService.preInscrireChauffeur(dto));
    }

    @PostMapping("/mot-de-passe/reset")
    public ResponseEntity<Map<String, String>> demanderReset(@Valid @RequestBody ResetPasswordDTO dto) {
        authService.demanderResetMotDePasse(dto);
        // Message identique que le compte existe ou non (pas d'énumération des emails)
        return ResponseEntity.ok(Map.of("message",
                "Si un compte existe pour cet email, un code de réinitialisation vient d'être envoyé."));
    }

    @PostMapping("/mot-de-passe/nouveau")
    public ResponseEntity<Map<String, String>> nouveauMotDePasse(@Valid @RequestBody NouveauMotDePasseDTO dto) {
        authService.reinitialiserMotDePasse(dto);
        return ResponseEntity.ok(Map.of("message", "Mot de passe modifié avec succès."));
    }

    // Changement de mot de passe par l'utilisateur connecté (obligatoire après
    // un mot de passe temporaire)
    @PutMapping("/mot-de-passe")
    public ResponseEntity<Map<String, String>> changerMotDePasse(@Valid @RequestBody ChangerMotDePasseDTO dto) {
        authService.changerMotDePasse(utilisateurCourant.get(), dto);
        return ResponseEntity.ok(Map.of("message", "Mot de passe modifié avec succès."));
    }

    // ── ADMIN ──────────────────────────────────────────────

    @PutMapping("/admin/chauffeurs/{chauffeurId}/valider")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> validerChauffeur(@PathVariable Long chauffeurId) {
        Map<String, Object> resultat = authService.validerChauffeur(chauffeurId);
        journalService.log("Validation du dossier chauffeur", "CHAUFFEUR", chauffeurId);
        return ResponseEntity.ok(resultat);
    }

    @PutMapping("/admin/chauffeurs/{chauffeurId}/bloquer")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> bloquerChauffeur(@PathVariable Long chauffeurId) {
        authService.bloquerChauffeur(chauffeurId);
        journalService.log("Blocage du chauffeur", "CHAUFFEUR", chauffeurId);
        return ResponseEntity.ok(Map.of("message", "Chauffeur bloqué"));
    }

    @PutMapping("/admin/chauffeurs/{chauffeurId}/debloquer")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> debloquerChauffeur(@PathVariable Long chauffeurId) {
        authService.debloquerChauffeur(chauffeurId);
        journalService.log("Déblocage du chauffeur", "CHAUFFEUR", chauffeurId);
        return ResponseEntity.ok(Map.of("message", "Chauffeur débloqué"));
    }

    @PutMapping("/admin/passagers/{passagerId}/bloquer")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> bloquerPassager(@PathVariable Long passagerId) {
        authService.bloquerPassager(passagerId);
        journalService.log("Blocage du passager", "PASSAGER", passagerId);
        return ResponseEntity.ok(Map.of("message", "Passager bloqué"));
    }

    @PutMapping("/admin/passagers/{passagerId}/debloquer")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> debloquerPassager(@PathVariable Long passagerId) {
        authService.debloquerPassager(passagerId);
        journalService.log("Déblocage du passager", "PASSAGER", passagerId);
        return ResponseEntity.ok(Map.of("message", "Passager débloqué"));
    }
}

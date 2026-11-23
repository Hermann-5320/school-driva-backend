package auth_service.service;

import auth_service.dto.*;
import auth_service.entity.*;
import auth_service.repository.*;
import auth_service.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UtilisateurRepository utilisateurRepository;
    private final PassagerRepository passagerRepository;
    private final ChauffeurRepository chauffeurRepository;
    private final VilleRepository villeRepository;
    private final VehiculeRepository vehiculeRepository;
    private final AdminRepository adminRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final DocumentService documentService;
    private final DossierChauffeurService dossierChauffeurService;

    private static final SecureRandom RANDOM = new SecureRandom();
    // Lettres/chiffres sans caractères ambigus (0/O, 1/l/I)
    private static final String ALPHABET_MDP = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final int MAX_ECHECS_CONNEXION = 5;
    private static final int MAX_ESSAIS_CODE = 5;
    // Compteur d'essais de code de reset par email (une seule instance sur Render)
    private final Map<String, Integer> essaisCodeReset = new ConcurrentHashMap<>();

    private volatile String hashFactice;

    // Hash calculé une fois : sert à égaliser le temps de réponse quand l'email
    // n'existe pas (évite de deviner les comptes au chronomètre).
    private String hashFactice() {
        if (hashFactice == null) hashFactice = passwordEncoder.encode(genererChaineAleatoire(16));
        return hashFactice;
    }

    private static String normaliserEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private Utilisateur trouverParEmail(String email) {
        // Les anciens comptes ont pu être enregistrés avec majuscules : double recherche
        String normalise = normaliserEmail(email);
        return utilisateurRepository.findByEmail(normalise)
                .or(() -> utilisateurRepository.findByEmail(email == null ? "" : email.trim()))
                .orElse(null);
    }

    // ── CONNEXION ──────────────────────────────────────────
    @Transactional(noRollbackFor = RuntimeException.class)
    public TokenDTO connecter(ConnexionDTO dto) {
        Utilisateur utilisateur = trouverParEmail(dto.getEmail());
        if (utilisateur == null) {
            // Même temps de réponse approximatif que pour un compte existant
            passwordEncoder.matches(dto.getMotDePasse(), hashFactice());
            throw new RuntimeException("Email ou mot de passe incorrect");
        }

        LocalDateTime maintenant = LocalDateTime.now();
        if (utilisateur.getVerrouilleJusquA() != null && utilisateur.getVerrouilleJusquA().isAfter(maintenant)) {
            long minutes = Duration.between(maintenant, utilisateur.getVerrouilleJusquA()).toMinutes() + 1;
            throw new RuntimeException("Compte temporairement verrouillé. Réessayez dans " + minutes + " minute(s).");
        }

        if (!passwordEncoder.matches(dto.getMotDePasse(), utilisateur.getMotDePasse())) {
            int tentatives = (utilisateur.getTentativesEchouees() == null ? 0 : utilisateur.getTentativesEchouees()) + 1;
            utilisateur.setTentativesEchouees(tentatives);
            if (tentatives >= MAX_ECHECS_CONNEXION) {
                utilisateur.setVerrouilleJusquA(maintenant.plusMinutes(15));
                utilisateur.setTentativesEchouees(0);
            }
            utilisateurRepository.save(utilisateur);
            throw new RuntimeException("Email ou mot de passe incorrect");
        }

        if (utilisateur.getStatut() == Utilisateur.Statut.BLOQUE) {
            // Chauffeur pré-inscrit non encore validé : message explicite
            boolean chauffeurEnAttente = utilisateur.getRole() == Utilisateur.Role.CHAUFFEUR
                    && chauffeurRepository.findByUtilisateurId(utilisateur.getId())
                    .map(c -> c.getStatut() == Chauffeur.Statut.EN_ATTENTE).orElse(false);
            throw new RuntimeException(chauffeurEnAttente
                    ? "Votre dossier est en cours de validation : vous recevrez vos identifiants par email. "
                      + "Document manquant ou rejeté ? Utilisez « Compléter mon dossier chauffeur »."
                    : "Votre compte est bloqué. Contactez le support.");
        }

        if (utilisateur.getRole() == Utilisateur.Role.ADMIN) {
            adminRepository.findByUtilisateurId(utilisateur.getId())
                    .filter(a -> "BLOQUE".equals(a.getStatut()))
                    .ifPresent(a -> { throw new RuntimeException("Ce compte administrateur est désactivé."); });
        }

        utilisateur.setTentativesEchouees(0);
        utilisateur.setVerrouilleJusquA(null);
        utilisateurRepository.save(utilisateur);

        String token = jwtService.genererToken(utilisateur.getEmail(), utilisateur.getRole().name(), utilisateur.getId());
        return new TokenDTO(token, utilisateur.getRole().name(), utilisateur.getId(),
                Boolean.TRUE.equals(utilisateur.getDoitChangerMotDePasse()));
    }

    // ── INSCRIPTION PASSAGER ───────────────────────────────
    @Transactional
    public void inscrirePassager(InscriptionPassagerDTO dto) {
        String email = normaliserEmail(dto.getEmail());
        if (trouverParEmail(email) != null) {
            throw new RuntimeException("Cet email est déjà utilisé");
        }

        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setEmail(email);
        utilisateur.setMotDePasse(passwordEncoder.encode(dto.getMotDePasse()));
        utilisateur.setRole(Utilisateur.Role.PASSAGER);
        utilisateur = utilisateurRepository.save(utilisateur);

        Passager passager = new Passager();
        passager.setUtilisateur(utilisateur);
        passager.setNom(dto.getNom().trim().toUpperCase());
        passager.setPrenom(dto.getPrenom().trim());
        passager.setTelephone(dto.getTelephone().trim());
        if (dto.getVilleId() != null) {
            villeRepository.findById(dto.getVilleId()).ifPresent(passager::setVille);
        }
        passagerRepository.save(passager);

        emailService.envoyerConfirmationInscription(email, dto.getPrenom());
    }

    // ── PRE-INSCRIPTION CHAUFFEUR ──────────────────────────
    @Transactional
    public Map<String, Object> preInscrireChauffeur(PreInscriptionChauffeurDTO dto) {
        String email = normaliserEmail(dto.getEmail());
        if (trouverParEmail(email) != null) {
            throw new RuntimeException("Cet email est déjà utilisé");
        }
        String type = dto.getTypeVehicule().trim().toUpperCase();
        if (!List.of("VOITURE", "MOTO").contains(type)) {
            throw new RuntimeException("Type de véhicule invalide (VOITURE ou MOTO)");
        }

        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setEmail(email);
        // Mot de passe aléatoire inutilisable : compte BLOQUE jusqu'à validation admin
        utilisateur.setMotDePasse(passwordEncoder.encode(genererChaineAleatoire(32)));
        utilisateur.setRole(Utilisateur.Role.CHAUFFEUR);
        utilisateur.setStatut(Utilisateur.Statut.BLOQUE);
        utilisateur = utilisateurRepository.save(utilisateur);

        Chauffeur chauffeur = new Chauffeur();
        chauffeur.setUtilisateur(utilisateur);
        chauffeur.setNom(dto.getNom().trim().toUpperCase());
        chauffeur.setPrenom(dto.getPrenom().trim());
        chauffeur.setTelephone(dto.getTelephone().trim());
        chauffeur.setStatut(Chauffeur.Statut.EN_ATTENTE);
        if (dto.getVilleId() != null) {
            villeRepository.findById(dto.getVilleId()).ifPresent(chauffeur::setVille);
        }
        chauffeur = chauffeurRepository.save(chauffeur);

        Vehicule vehicule = new Vehicule();
        vehicule.setChauffeur(chauffeur);
        vehicule.setType(type);
        vehicule.setMarque(dto.getMarque());
        vehicule.setModele(dto.getModele());
        vehicule.setCouleur(dto.getCouleur());
        vehicule.setAnnee(dto.getAnnee());
        vehicule.setImmatriculation(dto.getImmatriculation().trim().toUpperCase());
        vehiculeRepository.save(vehicule);

        emailService.envoyerConfirmationDossier(email, dto.getPrenom(), dossierChauffeurService.lienDossier(chauffeur.getId()));

        return Map.of(
                "chauffeurId", chauffeur.getId(),
                "uploadToken", jwtService.genererTokenUpload(chauffeur.getId())
        );
    }

    // ── DEMANDER UN RESET DE MOT DE PASSE ─────────────────
    // Ne révèle jamais si l'email existe (réponse identique côté contrôleur).
    @Transactional
    public void demanderResetMotDePasse(ResetPasswordDTO dto) {
        Utilisateur utilisateur = trouverParEmail(dto.getEmail());
        if (utilisateur == null) return;

        // Anti-spam : un code au plus par minute
        if (utilisateur.getCodeResetExpire() != null
                && utilisateur.getCodeResetExpire().isAfter(LocalDateTime.now().plusMinutes(14))) {
            return;
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        utilisateur.setCodeReset(passwordEncoder.encode(code)); // stocké haché
        utilisateur.setCodeResetExpire(LocalDateTime.now().plusMinutes(15));
        utilisateurRepository.save(utilisateur);
        essaisCodeReset.remove(utilisateur.getEmail());
        emailService.envoyerCodeReset(utilisateur.getEmail(), code);
    }

    // ── REINITIALISER LE MOT DE PASSE ─────────────────────
    @Transactional(noRollbackFor = RuntimeException.class)
    public void reinitialiserMotDePasse(NouveauMotDePasseDTO dto) {
        Utilisateur utilisateur = trouverParEmail(dto.getEmail());
        if (utilisateur == null || utilisateur.getCodeReset() == null || utilisateur.getCodeResetExpire() == null
                || utilisateur.getCodeResetExpire().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Code invalide ou expiré. Veuillez en demander un nouveau.");
        }

        if (dto.getCode() == null || !passwordEncoder.matches(dto.getCode().trim(), utilisateur.getCodeReset())) {
            int essais = essaisCodeReset.merge(utilisateur.getEmail(), 1, Integer::sum);
            if (essais >= MAX_ESSAIS_CODE) {
                utilisateur.setCodeReset(null);
                utilisateur.setCodeResetExpire(null);
                utilisateurRepository.save(utilisateur);
                essaisCodeReset.remove(utilisateur.getEmail());
                throw new RuntimeException("Trop de tentatives. Demandez un nouveau code.");
            }
            throw new RuntimeException("Code invalide ou expiré");
        }

        utilisateur.setMotDePasse(passwordEncoder.encode(dto.getNouveauMotDePasse()));
        utilisateur.setCodeReset(null);
        utilisateur.setCodeResetExpire(null);
        utilisateur.setTentativesEchouees(0);
        utilisateur.setVerrouilleJusquA(null);
        utilisateur.setDoitChangerMotDePasse(false);
        utilisateurRepository.save(utilisateur);
        essaisCodeReset.remove(utilisateur.getEmail());
    }

    // ── CHANGER SON MOT DE PASSE (connecté) ───────────────
    @Transactional
    public void changerMotDePasse(Utilisateur utilisateur, ChangerMotDePasseDTO dto) {
        if (!passwordEncoder.matches(dto.getAncienMotDePasse(), utilisateur.getMotDePasse())) {
            throw new RuntimeException("Mot de passe actuel incorrect");
        }
        if (dto.getAncienMotDePasse().equals(dto.getNouveauMotDePasse())) {
            throw new RuntimeException("Le nouveau mot de passe doit être différent de l'ancien");
        }
        utilisateur.setMotDePasse(passwordEncoder.encode(dto.getNouveauMotDePasse()));
        utilisateur.setDoitChangerMotDePasse(false);
        utilisateurRepository.save(utilisateur);
    }

    // ── VALIDER UN CHAUFFEUR (ADMIN) ───────────────────────
    @Transactional
    public Map<String, Object> validerChauffeur(Long chauffeurId) {
        Chauffeur chauffeur = chauffeurRepository.findById(chauffeurId)
                .orElseThrow(() -> new RuntimeException("Chauffeur introuvable"));
        if (chauffeur.getStatut() != Chauffeur.Statut.EN_ATTENTE) {
            throw new RuntimeException("Ce dossier n'est pas en attente de validation");
        }
        List<String> manquants = documentService.documentsObligatoiresNonValides(chauffeurId);
        if (!manquants.isEmpty()) {
            throw new RuntimeException("Documents obligatoires non validés : " + String.join(", ", manquants));
        }

        Utilisateur utilisateur = chauffeur.getUtilisateur();
        String motDePasseTemp = "SD-" + genererChaineAleatoire(10);
        utilisateur.setMotDePasse(passwordEncoder.encode(motDePasseTemp));
        utilisateur.setStatut(Utilisateur.Statut.ACTIF);
        utilisateur.setDoitChangerMotDePasse(true);
        utilisateurRepository.save(utilisateur);

        chauffeur.setStatut(Chauffeur.Statut.ACTIF);
        chauffeurRepository.save(chauffeur);

        boolean envoye = emailService.envoyerActivationCompte(utilisateur.getEmail(), chauffeur.getPrenom(), motDePasseTemp);

        Map<String, Object> resultat = new HashMap<>();
        resultat.put("message", "Chauffeur validé et activé");
        resultat.put("emailEnvoye", envoye);
        // Si l'email n'a pas pu partir, l'admin reçoit le mot de passe temporaire
        // pour le transmettre lui-même (il devra être changé à la 1re connexion).
        if (!envoye) resultat.put("motDePasseTemporaire", motDePasseTemp);
        return resultat;
    }

    // ── BLOQUER / DEBLOQUER (ADMIN) ────────────────────────
    @Transactional
    public void bloquerChauffeur(Long chauffeurId) {
        Chauffeur chauffeur = chauffeurRepository.findById(chauffeurId)
                .orElseThrow(() -> new RuntimeException("Chauffeur introuvable"));
        chauffeur.getUtilisateur().setStatut(Utilisateur.Statut.BLOQUE);
        utilisateurRepository.save(chauffeur.getUtilisateur());
        chauffeur.setStatut(Chauffeur.Statut.BLOQUE);
        chauffeur.setEnLigne(false);
        chauffeurRepository.save(chauffeur);
    }

    @Transactional
    public void debloquerChauffeur(Long chauffeurId) {
        Chauffeur chauffeur = chauffeurRepository.findById(chauffeurId)
                .orElseThrow(() -> new RuntimeException("Chauffeur introuvable"));
        if (chauffeur.getStatut() != Chauffeur.Statut.BLOQUE) {
            throw new RuntimeException("Ce chauffeur n'est pas bloqué");
        }
        chauffeur.getUtilisateur().setStatut(Utilisateur.Statut.ACTIF);
        utilisateurRepository.save(chauffeur.getUtilisateur());
        chauffeur.setStatut(Chauffeur.Statut.ACTIF);
        chauffeurRepository.save(chauffeur);
    }

    @Transactional
    public void bloquerPassager(Long passagerId) {
        Passager passager = passagerRepository.findById(passagerId)
                .orElseThrow(() -> new RuntimeException("Passager introuvable"));
        passager.getUtilisateur().setStatut(Utilisateur.Statut.BLOQUE);
        utilisateurRepository.save(passager.getUtilisateur());
    }

    @Transactional
    public void debloquerPassager(Long passagerId) {
        Passager passager = passagerRepository.findById(passagerId)
                .orElseThrow(() -> new RuntimeException("Passager introuvable"));
        passager.getUtilisateur().setStatut(Utilisateur.Statut.ACTIF);
        utilisateurRepository.save(passager.getUtilisateur());
    }

    // ── ADMINISTRATEURS (SUPER_ADMIN) ──────────────────────
    public Admin adminCourant(Utilisateur utilisateur) {
        return adminRepository.findByUtilisateurId(utilisateur.getId())
                .orElseThrow(() -> new AccessDeniedException("Profil administrateur introuvable"));
    }

    @Transactional
    public Map<String, Object> creerAdmin(Utilisateur createur, CreerAdminDTO dto) {
        Admin superAdmin = adminCourant(createur);
        if (!"SUPER_ADMIN".equals(superAdmin.getRole())) {
            throw new AccessDeniedException("Seul un super administrateur peut créer des administrateurs");
        }
        String email = normaliserEmail(dto.getEmail());
        if (trouverParEmail(email) != null) {
            throw new RuntimeException("Cet email est déjà utilisé");
        }
        String motDePasseTemp = "SD-" + genererChaineAleatoire(12);

        Utilisateur u = new Utilisateur();
        u.setEmail(email);
        u.setMotDePasse(passwordEncoder.encode(motDePasseTemp));
        u.setRole(Utilisateur.Role.ADMIN);
        u.setDoitChangerMotDePasse(true);
        u = utilisateurRepository.save(u);

        Admin admin = new Admin();
        admin.setUtilisateur(u);
        admin.setNom(dto.getNom().trim());
        admin.setRole("ADMIN");
        admin.setCreatedBy(superAdmin.getId());
        admin = adminRepository.save(admin);

        boolean envoye = emailService.envoyerIdentifiantsAdmin(email, admin.getNom(), motDePasseTemp);
        Map<String, Object> resultat = new HashMap<>();
        resultat.put("id", admin.getId());
        resultat.put("emailEnvoye", envoye);
        if (!envoye) resultat.put("motDePasseTemporaire", motDePasseTemp);
        return resultat;
    }

    @Transactional
    public void changerStatutAdmin(Utilisateur demandeur, Long adminId, boolean actif) {
        Admin superAdmin = adminCourant(demandeur);
        if (!"SUPER_ADMIN".equals(superAdmin.getRole())) {
            throw new AccessDeniedException("Seul un super administrateur peut modifier les administrateurs");
        }
        Admin cible = adminRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Administrateur introuvable"));
        if (cible.getId().equals(superAdmin.getId())) {
            throw new RuntimeException("Vous ne pouvez pas désactiver votre propre compte");
        }
        cible.setStatut(actif ? "ACTIF" : "BLOQUE");
        adminRepository.save(cible);
        cible.getUtilisateur().setStatut(actif ? Utilisateur.Statut.ACTIF : Utilisateur.Statut.BLOQUE);
        utilisateurRepository.save(cible.getUtilisateur());
    }

    private String genererChaineAleatoire(int longueur) {
        StringBuilder sb = new StringBuilder(longueur);
        for (int i = 0; i < longueur; i++) {
            sb.append(ALPHABET_MDP.charAt(RANDOM.nextInt(ALPHABET_MDP.length())));
        }
        return sb.toString();
    }
}

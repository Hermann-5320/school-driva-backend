package auth_service.controller;

import auth_service.dto.*;
import auth_service.entity.*;
import auth_service.repository.*;
import auth_service.security.UtilisateurCourant;
import auth_service.service.AuthService;
import auth_service.service.EmailService;
import auth_service.service.JournalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final ChauffeurRepository chauffeurRepository;
    private final PassagerRepository passagerRepository;
    private final DocumentRepository documentRepository;
    private final AdminRepository adminRepository;
    private final JournalAdminRepository journalRepository;
    private final PointCampusRepository pointCampusRepository;
    private final VilleRepository villeRepository;
    private final AuthService authService;
    private final EmailService emailService;
    private final JournalService journalService;
    private final UtilisateurCourant utilisateurCourant;

    @GetMapping("/dashboard-stats")
    public ResponseEntity<DashboardStatsDTO> getDashboardStats() {
        return ResponseEntity.ok(new DashboardStatsDTO(
                chauffeurRepository.count(),
                chauffeurRepository.countByStatut(Chauffeur.Statut.ACTIF),
                chauffeurRepository.countByStatut(Chauffeur.Statut.EN_ATTENTE),
                chauffeurRepository.countByStatut(Chauffeur.Statut.BLOQUE),
                chauffeurRepository.countByEnLigneTrue(),
                passagerRepository.count(),
                passagerRepository.countByUtilisateurStatut(Utilisateur.Statut.ACTIF),
                passagerRepository.countByUtilisateurStatut(Utilisateur.Statut.BLOQUE),
                documentRepository.countByStatut("EN_ATTENTE")
        ));
    }

    // ── Administrateurs ────────────────────────────────────

    @GetMapping("/moi")
    public ResponseEntity<Map<String, Object>> moi() {
        Admin admin = authService.adminCourant(utilisateurCourant.get());
        return ResponseEntity.ok(Map.of("id", admin.getId(), "nom", admin.getNom(), "role", admin.getRole()));
    }

    @GetMapping("/admins")
    public ResponseEntity<List<Map<String, Object>>> listeAdmins() {
        List<Map<String, Object>> resultat = adminRepository.findAll().stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("nom", a.getNom());
            m.put("email", a.getUtilisateur().getEmail());
            m.put("role", a.getRole());
            m.put("statut", a.getStatut());
            m.put("createdAt", a.getCreatedAt());
            return m;
        }).toList();
        return ResponseEntity.ok(resultat);
    }

    @PostMapping("/admins")
    public ResponseEntity<Map<String, Object>> creerAdmin(@Valid @RequestBody CreerAdminDTO dto) {
        Map<String, Object> resultat = authService.creerAdmin(utilisateurCourant.get(), dto);
        journalService.log("Création de l'administrateur " + dto.getEmail(), "ADMIN", (Long) resultat.get("id"));
        return ResponseEntity.ok(resultat);
    }

    @PutMapping("/admins/{id}/statut")
    public ResponseEntity<Map<String, String>> statutAdmin(@PathVariable Long id, @RequestParam boolean actif) {
        authService.changerStatutAdmin(utilisateurCourant.get(), id, actif);
        journalService.log((actif ? "Activation" : "Désactivation") + " d'un administrateur", "ADMIN", id);
        return ResponseEntity.ok(Map.of("message", actif ? "Administrateur activé" : "Administrateur désactivé"));
    }

    // ── Journal d'activité ─────────────────────────────────

    @GetMapping("/journal")
    public ResponseEntity<List<Map<String, Object>>> journal() {
        Map<Long, String> nomsAdmins = adminRepository.findAll().stream()
                .collect(Collectors.toMap(Admin::getId, Admin::getNom));
        List<Map<String, Object>> resultat = journalRepository.findTop50ByOrderByCreatedAtDesc().stream().map(j -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", j.getId());
            m.put("admin", nomsAdmins.getOrDefault(j.getAdminId(), "?"));
            m.put("action", j.getAction());
            m.put("entite", j.getEntite());
            m.put("entiteId", j.getEntiteId());
            m.put("createdAt", j.getCreatedAt());
            return m;
        }).toList();
        return ResponseEntity.ok(resultat);
    }

    // ── Message à un utilisateur (email) ───────────────────

    @PostMapping("/chauffeurs/{id}/message")
    public ResponseEntity<Map<String, Object>> messageChauffeur(@PathVariable Long id, @Valid @RequestBody MessageDTO dto) {
        Chauffeur c = chauffeurRepository.findById(id).orElseThrow(() -> new RuntimeException("Chauffeur introuvable"));
        boolean envoye = emailService.envoyer(c.getUtilisateur().getEmail(), dto.getSujet(), dto.getTexte());
        journalService.log("Message envoyé au chauffeur : " + dto.getSujet(), "CHAUFFEUR", id);
        return ResponseEntity.ok(Map.of("emailEnvoye", envoye));
    }

    @PostMapping("/passagers/{id}/message")
    public ResponseEntity<Map<String, Object>> messagePassager(@PathVariable Long id, @Valid @RequestBody MessageDTO dto) {
        Passager p = passagerRepository.findById(id).orElseThrow(() -> new RuntimeException("Passager introuvable"));
        boolean envoye = emailService.envoyer(p.getUtilisateur().getEmail(), dto.getSujet(), dto.getTexte());
        journalService.log("Message envoyé au passager : " + dto.getSujet(), "PASSAGER", id);
        return ResponseEntity.ok(Map.of("emailEnvoye", envoye));
    }

    // ── Points campus ──────────────────────────────────────

    @GetMapping("/campus")
    public ResponseEntity<List<PointCampus>> tousLesPointsCampus() {
        return ResponseEntity.ok(pointCampusRepository.findAllByOrderByNomAsc());
    }

    @PostMapping("/campus")
    public ResponseEntity<PointCampus> creerPointCampus(@Valid @RequestBody PointCampusDTO dto) {
        PointCampus p = appliquer(new PointCampus(), dto);
        p = pointCampusRepository.save(p);
        journalService.log("Ajout du point campus « " + p.getNom() + " »", "CAMPUS", p.getId());
        return ResponseEntity.ok(p);
    }

    @PutMapping("/campus/{id}")
    public ResponseEntity<PointCampus> modifierPointCampus(@PathVariable Long id, @Valid @RequestBody PointCampusDTO dto) {
        PointCampus p = pointCampusRepository.findById(id).orElseThrow(() -> new RuntimeException("Point introuvable"));
        p = pointCampusRepository.save(appliquer(p, dto));
        journalService.log("Modification du point campus « " + p.getNom() + " »", "CAMPUS", id);
        return ResponseEntity.ok(p);
    }

    @DeleteMapping("/campus/{id}")
    public ResponseEntity<Map<String, String>> supprimerPointCampus(@PathVariable Long id) {
        PointCampus p = pointCampusRepository.findById(id).orElseThrow(() -> new RuntimeException("Point introuvable"));
        pointCampusRepository.delete(p);
        journalService.log("Suppression du point campus « " + p.getNom() + " »", "CAMPUS", id);
        return ResponseEntity.ok(Map.of("message", "Point supprimé"));
    }

    private PointCampus appliquer(PointCampus p, PointCampusDTO dto) {
        if (!villeRepository.existsById(dto.getVilleId())) {
            throw new RuntimeException("Ville introuvable");
        }
        p.setVilleId(dto.getVilleId());
        p.setNom(dto.getNom().trim());
        p.setCategorie(dto.getCategorie());
        p.setAdresse(dto.getAdresse());
        p.setLat(dto.getLat());
        p.setLng(dto.getLng());
        p.setActif(dto.getActif() == null || dto.getActif());
        return p;
    }
}

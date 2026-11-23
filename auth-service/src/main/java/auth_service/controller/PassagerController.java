package auth_service.controller;

import auth_service.dto.CreerFavoriDTO;
import auth_service.dto.ModifierProfilPassagerDTO;
import auth_service.dto.PassagerAdminDTO;
import auth_service.dto.PassagerProfilDTO;
import auth_service.entity.Favori;
import auth_service.entity.Passager;
import auth_service.entity.Utilisateur;
import auth_service.repository.FavoriRepository;
import auth_service.repository.PassagerRepository;
import auth_service.repository.VilleRepository;
import auth_service.security.UtilisateurCourant;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/passagers")
@RequiredArgsConstructor
public class PassagerController {

    private static final int MAX_FAVORIS = 10;

    private final PassagerRepository passagerRepository;
    private final FavoriRepository favoriRepository;
    private final VilleRepository villeRepository;
    private final UtilisateurCourant utilisateurCourant;

    private Passager passagerCourant() {
        Utilisateur u = utilisateurCourant.get();
        return passagerRepository.findByUtilisateurId(u.getId())
                .orElseThrow(() -> new AccessDeniedException("Profil passager introuvable"));
    }

    private PassagerProfilDTO versDto(Passager p) {
        return new PassagerProfilDTO(p.getId(), p.getNom(), p.getPrenom(), p.getUtilisateur().getEmail(),
                p.getTelephone(),
                p.getVille() != null ? p.getVille().getId() : null,
                p.getVille() != null ? p.getVille().getNom() : null,
                p.getUtilisateur().getStatut().name(), p.getCreatedAt());
    }

    // INTERNE (clé X-Internal-Api-Key) : utilisateurId → passagerId
    @GetMapping("/by-utilisateur/{utilisateurId}")
    public ResponseEntity<Long> getPassagerId(@PathVariable Long utilisateurId) {
        Passager passager = passagerRepository.findByUtilisateurId(utilisateurId)
                .orElseThrow(() -> new RuntimeException("Passager introuvable"));
        return ResponseEntity.ok(passager.getId());
    }

    // INTERNE : infos du passager, filtrées ensuite par course-service
    @GetMapping("/{id}/public")
    public ResponseEntity<Map<String, Object>> getProfilPublic(@PathVariable Long id) {
        Passager passager = passagerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Passager introuvable"));
        Map<String, Object> profil = new HashMap<>();
        profil.put("id", passager.getId());
        profil.put("nom", passager.getNom());
        profil.put("prenom", passager.getPrenom());
        profil.put("telephone", passager.getTelephone());
        return ResponseEntity.ok(profil);
    }

    @GetMapping("/admin/liste")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PassagerAdminDTO>> listePassagersAdmin(@RequestParam(required = false) String statut) {
        List<PassagerAdminDTO> resultat = passagerRepository.findAll().stream()
                .filter(p -> statut == null || p.getUtilisateur().getStatut().name().equals(statut))
                .map(p -> new PassagerAdminDTO(p.getId(), p.getNom(), p.getPrenom(),
                        p.getUtilisateur().getEmail(), p.getTelephone(),
                        p.getVille() != null ? p.getVille().getNom() : null,
                        p.getUtilisateur().getStatut().name(), p.getCreatedAt()))
                .toList();
        return ResponseEntity.ok(resultat);
    }

    @GetMapping("/mon-profil")
    @PreAuthorize("hasRole('PASSAGER')")
    public ResponseEntity<PassagerProfilDTO> getMonProfil() {
        return ResponseEntity.ok(versDto(passagerCourant()));
    }

    @PutMapping("/mon-profil")
    @PreAuthorize("hasRole('PASSAGER')")
    public ResponseEntity<PassagerProfilDTO> modifierMonProfil(@Valid @RequestBody ModifierProfilPassagerDTO dto) {
        Passager p = passagerCourant();
        p.setNom(dto.getNom().trim().toUpperCase());
        p.setPrenom(dto.getPrenom().trim());
        p.setTelephone(dto.getTelephone().trim());
        if (dto.getVilleId() != null) {
            p.setVille(villeRepository.findById(dto.getVilleId())
                    .orElseThrow(() -> new RuntimeException("Ville introuvable")));
        }
        return ResponseEntity.ok(versDto(passagerRepository.save(p)));
    }

    @GetMapping("/mes-favoris")
    @PreAuthorize("hasRole('PASSAGER')")
    public ResponseEntity<List<Favori>> getMesFavoris() {
        return ResponseEntity.ok(favoriRepository.findByPassagerId(passagerCourant().getId()));
    }

    @PostMapping("/mes-favoris")
    @PreAuthorize("hasRole('PASSAGER')")
    public ResponseEntity<Favori> ajouterFavori(@Valid @RequestBody CreerFavoriDTO dto) {
        Passager passager = passagerCourant();
        if (favoriRepository.findByPassagerId(passager.getId()).size() >= MAX_FAVORIS) {
            throw new RuntimeException("Maximum " + MAX_FAVORIS + " favoris");
        }
        Favori favori = new Favori();
        favori.setPassagerId(passager.getId());
        favori.setLabel(dto.getLabel());
        favori.setAdresse(dto.getAdresse());
        favori.setLat(dto.getLat());
        favori.setLng(dto.getLng());
        return ResponseEntity.ok(favoriRepository.save(favori));
    }

    @DeleteMapping("/mes-favoris/{id}")
    @PreAuthorize("hasRole('PASSAGER')")
    public ResponseEntity<Map<String, String>> supprimerFavori(@PathVariable Long id) {
        Passager passager = passagerCourant();
        Favori favori = favoriRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Favori introuvable"));
        if (!favori.getPassagerId().equals(passager.getId())) {
            throw new AccessDeniedException("Ce favori ne vous appartient pas");
        }
        favoriRepository.delete(favori);
        return ResponseEntity.ok(Map.of("message", "Favori supprimé"));
    }
}

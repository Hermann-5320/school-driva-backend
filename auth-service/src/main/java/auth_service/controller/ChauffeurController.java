package auth_service.controller;

import auth_service.dto.ChauffeurAdminDTO;
import auth_service.dto.ChauffeurDisponibleDTO;
import auth_service.dto.ChauffeurProfilDTO;
import auth_service.dto.ModifierProfilChauffeurDTO;
import auth_service.entity.Chauffeur;
import auth_service.entity.Document;
import auth_service.entity.Utilisateur;
import auth_service.entity.Vehicule;
import auth_service.repository.ChauffeurRepository;
import auth_service.repository.DocumentRepository;
import auth_service.repository.VehiculeRepository;
import auth_service.security.UtilisateurCourant;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chauffeurs")
@RequiredArgsConstructor
public class ChauffeurController {

    private final ChauffeurRepository chauffeurRepository;
    private final VehiculeRepository vehiculeRepository;
    private final DocumentRepository documentRepository;
    private final UtilisateurCourant utilisateurCourant;

    private Chauffeur chauffeurCourant() {
        Utilisateur u = utilisateurCourant.get();
        return chauffeurRepository.findByUtilisateurId(u.getId())
                .orElseThrow(() -> new AccessDeniedException("Profil chauffeur introuvable"));
    }

    private ChauffeurProfilDTO versProfil(Chauffeur c) {
        Optional<Vehicule> v = vehiculeRepository.findByChauffeurId(c.getId());
        return new ChauffeurProfilDTO(c.getId(), c.getNom(), c.getPrenom(), c.getUtilisateur().getEmail(),
                c.getTelephone(),
                c.getVille() != null ? c.getVille().getId() : null,
                c.getVille() != null ? c.getVille().getNom() : null,
                c.getStatut().name(), c.getEnLigne(), c.getNoteMoyenne(), c.getNbCourses(), c.getKmTotal(),
                c.getNumeroMtn(), c.getNumeroOrange(),
                v.map(Vehicule::getType).orElse(null),
                v.map(Vehicule::getMarque).orElse(null),
                v.map(Vehicule::getModele).orElse(null),
                v.map(Vehicule::getCouleur).orElse(null),
                v.map(Vehicule::getImmatriculation).orElse(null));
    }

    // Profil public minimal (identité, note, véhicule, numéros Mobile Money
    // pour le paiement), visible par les utilisateurs connectés.
    @GetMapping("/{id}/public")
    @PreAuthorize("hasAnyRole('PASSAGER','CHAUFFEUR','ADMIN')")
    public ResponseEntity<Map<String, Object>> getProfilPublic(@PathVariable Long id) {
        Chauffeur chauffeur = chauffeurRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Chauffeur introuvable"));
        Map<String, Object> profil = new HashMap<>();
        profil.put("id", chauffeur.getId());
        profil.put("nom", chauffeur.getNom());
        profil.put("prenom", chauffeur.getPrenom());
        profil.put("telephone", chauffeur.getTelephone());
        profil.put("noteMoyenne", chauffeur.getNoteMoyenne());
        profil.put("nbCourses", chauffeur.getNbCourses());
        profil.put("numeroMtn", chauffeur.getNumeroMtn());
        profil.put("numeroOrange", chauffeur.getNumeroOrange());
        vehiculeRepository.findByChauffeurId(id).ifPresent(v -> {
            profil.put("typeVehicule", v.getType());
            profil.put("vehiculeMarque", v.getMarque());
            profil.put("vehiculeModele", v.getModele());
            profil.put("vehiculeCouleur", v.getCouleur());
            profil.put("immatriculation", v.getImmatriculation());
        });
        return ResponseEntity.ok(profil);
    }

    // INTERNE (clé X-Internal-Api-Key) : utilisateurId → chauffeurId
    @GetMapping("/by-utilisateur/{utilisateurId}")
    public ResponseEntity<Long> getChauffeurId(@PathVariable Long utilisateurId) {
        Chauffeur chauffeur = chauffeurRepository.findByUtilisateurId(utilisateurId)
                .orElseThrow(() -> new RuntimeException("Chauffeur introuvable"));
        return ResponseEntity.ok(chauffeur.getId());
    }

    // Chauffeurs en ligne d'une ville (sans téléphone : inutile au passager à ce stade)
    @GetMapping("/en-ligne")
    public ResponseEntity<List<ChauffeurDisponibleDTO>> getChauffeursEnLigne(@RequestParam Long villeId) {
        List<ChauffeurDisponibleDTO> resultat = chauffeurRepository
                .findByVille_IdAndEnLigneTrueAndStatut(villeId, Chauffeur.Statut.ACTIF).stream()
                .map(c -> new ChauffeurDisponibleDTO(c.getId(), c.getNom(), c.getPrenom(), null,
                        c.getNoteMoyenne(), c.getVille().getNom(),
                        vehiculeRepository.findByChauffeurId(c.getId()).map(Vehicule::getType).orElse(null)))
                .toList();
        return ResponseEntity.ok(resultat);
    }

    @PutMapping("/statut-ligne")
    @PreAuthorize("hasRole('CHAUFFEUR')")
    public ResponseEntity<Map<String, Object>> basculerStatutLigne(@RequestParam Boolean enLigne) {
        Chauffeur chauffeur = chauffeurCourant();
        if (Boolean.TRUE.equals(enLigne) && chauffeur.getStatut() != Chauffeur.Statut.ACTIF) {
            throw new RuntimeException("Votre compte doit être actif pour passer en ligne");
        }
        if (Boolean.TRUE.equals(enLigne) && chauffeur.getVille() == null) {
            throw new RuntimeException("Aucune ville associée à votre compte. Contactez le support.");
        }
        chauffeur.setEnLigne(Boolean.TRUE.equals(enLigne));
        chauffeurRepository.save(chauffeur);
        return ResponseEntity.ok(Map.of("enLigne", chauffeur.getEnLigne(),
                "message", chauffeur.getEnLigne() ? "Vous êtes en ligne" : "Vous êtes hors ligne"));
    }

    // Liste complète des chauffeurs (Admin), avec véhicule et état des documents
    @GetMapping("/admin/liste")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ChauffeurAdminDTO>> listeChauffeursAdmin(@RequestParam(required = false) String statut) {
        List<Chauffeur> chauffeurs;
        if (statut != null && !statut.isBlank()) {
            try {
                chauffeurs = chauffeurRepository.findByStatut(Chauffeur.Statut.valueOf(statut));
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Statut invalide : " + statut);
            }
        } else {
            chauffeurs = chauffeurRepository.findAll();
        }
        Map<Long, Vehicule> vehicules = vehiculeRepository.findAll().stream()
                .collect(Collectors.toMap(v -> v.getChauffeur().getId(), v -> v, (a, b) -> a));
        Map<Long, List<Document>> docs = documentRepository.findAll().stream()
                .collect(Collectors.groupingBy(Document::getChauffeurId));

        List<ChauffeurAdminDTO> resultat = chauffeurs.stream().map(c -> {
            Vehicule v = vehicules.get(c.getId());
            List<Document> d = docs.getOrDefault(c.getId(), List.of());
            return new ChauffeurAdminDTO(c.getId(), c.getUtilisateur().getId(), c.getNom(), c.getPrenom(),
                    c.getUtilisateur().getEmail(), c.getTelephone(),
                    c.getVille() != null ? c.getVille().getId() : null,
                    c.getVille() != null ? c.getVille().getNom() : null,
                    c.getStatut().name(), c.getEnLigne(), c.getNoteMoyenne(), c.getNbCourses(), c.getCreatedAt(),
                    v != null ? v.getType() : null, v != null ? v.getMarque() : null,
                    v != null ? v.getModele() : null, v != null ? v.getCouleur() : null,
                    v != null ? v.getImmatriculation() : null,
                    d.stream().filter(x -> "VALIDE".equals(x.getStatut())).count(),
                    d.stream().filter(x -> "EN_ATTENTE".equals(x.getStatut())).count(),
                    d.stream().filter(x -> "REJETE".equals(x.getStatut())).count());
        }).toList();
        return ResponseEntity.ok(resultat);
    }

    // INTERNE (clé X-Internal-Api-Key) : mise à jour des stats après une course
    @PutMapping("/{chauffeurId}/stats")
    public ResponseEntity<Map<String, String>> mettreAJourStats(@PathVariable Long chauffeurId,
                                                                @RequestBody Map<String, Object> body) {
        Chauffeur chauffeur = chauffeurRepository.findById(chauffeurId)
                .orElseThrow(() -> new RuntimeException("Chauffeur introuvable"));

        if (body.get("noteMoyenne") instanceof Number note) {
            double n = Math.max(0, Math.min(5, note.doubleValue()));
            chauffeur.setNoteMoyenne(BigDecimal.valueOf(n).setScale(2, RoundingMode.HALF_UP));
        }
        if (Boolean.TRUE.equals(body.get("incrementerCourses"))) {
            chauffeur.setNbCourses((chauffeur.getNbCourses() == null ? 0 : chauffeur.getNbCourses()) + 1);
        }
        if (body.get("kmAAjouter") instanceof Number km && km.doubleValue() > 0) {
            BigDecimal total = chauffeur.getKmTotal() == null ? BigDecimal.ZERO : chauffeur.getKmTotal();
            chauffeur.setKmTotal(total.add(BigDecimal.valueOf(km.doubleValue())));
        }
        chauffeurRepository.save(chauffeur);
        return ResponseEntity.ok(Map.of("message", "Stats mises à jour"));
    }

    @GetMapping("/mon-profil")
    @PreAuthorize("hasRole('CHAUFFEUR')")
    public ResponseEntity<ChauffeurProfilDTO> getMonProfil() {
        return ResponseEntity.ok(versProfil(chauffeurCourant()));
    }

    @PutMapping("/mon-profil")
    @PreAuthorize("hasRole('CHAUFFEUR')")
    public ResponseEntity<ChauffeurProfilDTO> modifierMonProfil(@Valid @RequestBody ModifierProfilChauffeurDTO dto) {
        Chauffeur c = chauffeurCourant();
        c.setTelephone(dto.getTelephone().trim());
        c.setNumeroMtn(dto.getNumeroMtn() == null || dto.getNumeroMtn().isBlank() ? null : dto.getNumeroMtn().trim());
        c.setNumeroOrange(dto.getNumeroOrange() == null || dto.getNumeroOrange().isBlank() ? null : dto.getNumeroOrange().trim());
        return ResponseEntity.ok(versProfil(chauffeurRepository.save(c)));
    }
}

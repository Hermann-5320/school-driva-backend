package auth_service.controller;

import auth_service.dto.JournalInterneDTO;
import auth_service.entity.Chauffeur;
import auth_service.repository.ChauffeurRepository;
import auth_service.repository.VehiculeRepository;
import auth_service.service.JournalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Endpoints service-à-service, protégés par X-Internal-Api-Key
 * (InternalApiKeyFilter) et non exposés par le gateway.
 */
@RestController
@RequestMapping("/api/interne")
@RequiredArgsConstructor
public class InterneController {

    private final ChauffeurRepository chauffeurRepository;
    private final VehiculeRepository vehiculeRepository;
    private final JournalService journalService;

    // Résumé d'un chauffeur : type de véhicule, ville, identité, véhicule
    @GetMapping("/chauffeurs/{id}/resume")
    public ResponseEntity<Map<String, Object>> resumeChauffeur(@PathVariable Long id) {
        Chauffeur c = chauffeurRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Chauffeur introuvable"));
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.getId());
        m.put("prenom", c.getPrenom());
        m.put("nom", c.getNom());
        m.put("villeId", c.getVille() != null ? c.getVille().getId() : null);
        m.put("statut", c.getStatut().name());
        m.put("noteMoyenne", c.getNoteMoyenne());
        vehiculeRepository.findByChauffeurId(id).ifPresent(v -> {
            m.put("typeVehicule", v.getType());
            m.put("marque", v.getMarque());
            m.put("modele", v.getModele());
            m.put("couleur", v.getCouleur());
            m.put("immatriculation", v.getImmatriculation());
        });
        return ResponseEntity.ok(m);
    }

    // Nombre de chauffeurs en ligne d'une ville pour un type de véhicule
    // (course-service refuse une réservation si personne ne peut la prendre)
    @GetMapping("/chauffeurs/en-ligne/nombre")
    public ResponseEntity<Map<String, Long>> nombreEnLigne(@RequestParam Long villeId, @RequestParam String typeVehicule) {
        long nombre = chauffeurRepository.findByVille_IdAndEnLigneTrueAndStatut(villeId, Chauffeur.Statut.ACTIF).stream()
                .filter(c -> vehiculeRepository.findByChauffeurId(c.getId())
                        .map(v -> v.getType().equalsIgnoreCase(typeVehicule)).orElse(false))
                .count();
        return ResponseEntity.ok(Map.of("nombre", nombre));
    }

    @PostMapping("/journal")
    public ResponseEntity<Void> journal(@Valid @RequestBody JournalInterneDTO dto) {
        journalService.logPourEmail(dto.getEmail(), dto.getAction(), dto.getEntite(), dto.getEntiteId());
        return ResponseEntity.ok().build();
    }
}

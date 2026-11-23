package course_service.controller;

import course_service.client.AuthServiceClient;
import course_service.dto.ModifierParametreDTO;
import course_service.entity.ParametreSysteme;
import course_service.repository.ParametreSystemeRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/parametres")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ParametreController {

    // Bornes acceptées pour chaque paramètre (min, max) : évite une commission
    // à 500 % ou un tarif négatif saisi par erreur.
    private static final Map<String, BigDecimal[]> BORNES = Map.of(
            "COMMISSION_POURCENTAGE", new BigDecimal[]{BigDecimal.ZERO, new BigDecimal("50")},
            "TARIF_VOITURE_KM", new BigDecimal[]{new BigDecimal("50"), new BigDecimal("5000")},
            "TARIF_MOTO_KM", new BigDecimal[]{new BigDecimal("50"), new BigDecimal("5000")},
            "PRIX_MINIMUM", new BigDecimal[]{new BigDecimal("100"), new BigDecimal("20000")},
            "SOLDE_MINIMUM_CHAUFFEUR", new BigDecimal[]{BigDecimal.ZERO, new BigDecimal("100000")},
            "DELAI_RECHERCHE_MIN", new BigDecimal[]{new BigDecimal("2"), new BigDecimal("60")},
            "DISTANCE_MAX_KM", new BigDecimal[]{BigDecimal.ONE, new BigDecimal("300")},
            "TIMER_COURSE", new BigDecimal[]{new BigDecimal("5"), new BigDecimal("120")},
            "RAYON_DESSERTE_KM", new BigDecimal[]{BigDecimal.ONE, new BigDecimal("200")},
            "ITINERAIRE_PREFERENCE", new BigDecimal[]{BigDecimal.ONE, new BigDecimal("3")}
    );

    private final ParametreSystemeRepository parametreRepository;
    private final AuthServiceClient authServiceClient;

    @GetMapping
    public ResponseEntity<List<ParametreSysteme>> getTousParametres() {
        return ResponseEntity.ok(parametreRepository.findAll().stream()
                .filter(p -> BORNES.containsKey(p.getCle()))
                .sorted((a, b) -> a.getCle().compareTo(b.getCle()))
                .toList());
    }

    @PutMapping("/{cle}")
    public ResponseEntity<ParametreSysteme> modifierParametre(@PathVariable String cle,
                                                              @Valid @RequestBody ModifierParametreDTO dto) {
        ParametreSysteme parametre = parametreRepository.findById(cle)
                .orElseThrow(() -> new RuntimeException("Paramètre introuvable"));
        BigDecimal[] bornes = BORNES.get(cle);
        if (bornes == null) {
            throw new RuntimeException("Ce paramètre n'est pas modifiable");
        }
        BigDecimal valeur;
        try {
            valeur = new BigDecimal(dto.getValeur().trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new RuntimeException("Valeur numérique attendue");
        }
        if (valeur.compareTo(bornes[0]) < 0 || valeur.compareTo(bornes[1]) > 0) {
            throw new RuntimeException("Valeur hors limites (" + bornes[0] + " à " + bornes[1] + ")");
        }
        String ancienne = parametre.getValeur();
        parametre.setValeur(valeur.stripTrailingZeros().toPlainString());
        parametre.setUpdatedAt(LocalDateTime.now());
        parametreRepository.save(parametre);

        authServiceClient.journaliser(SecurityContextHolder.getContext().getAuthentication().getName(),
                "Paramètre " + cle + " : " + ancienne + " → " + parametre.getValeur(), "PARAMETRE", null);
        return ResponseEntity.ok(parametre);
    }
}

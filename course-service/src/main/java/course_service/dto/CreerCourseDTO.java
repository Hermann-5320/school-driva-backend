package course_service.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreerCourseDTO {

    @NotBlank(message = "Type de véhicule obligatoire")
    @Pattern(regexp = "(?i)^(VOITURE|MOTO)$", message = "Type de véhicule : VOITURE ou MOTO")
    private String typeVehicule;

    @NotBlank(message = "Adresse de départ obligatoire")
    @Size(max = 255)
    private String departAdresse;

    // Coordonnées obligatoires : sans elles le prix ne peut pas être calculé
    // (avant, une course sans coordonnées était facturée au prix minimum).
    @NotNull(message = "Position de départ obligatoire")
    @DecimalMin(value = "1.5", message = "Départ hors zone desservie")
    @DecimalMax(value = "13.5", message = "Départ hors zone desservie")
    private BigDecimal departLat;

    @NotNull(message = "Position de départ obligatoire")
    @DecimalMin(value = "8.0", message = "Départ hors zone desservie")
    @DecimalMax(value = "16.5", message = "Départ hors zone desservie")
    private BigDecimal departLng;

    @NotBlank(message = "Adresse d'arrivée obligatoire")
    @Size(max = 255)
    private String arriveeAdresse;

    @NotNull(message = "Destination obligatoire")
    @DecimalMin(value = "1.5", message = "Destination hors zone desservie")
    @DecimalMax(value = "13.5", message = "Destination hors zone desservie")
    private BigDecimal arriveeLat;

    @NotNull(message = "Destination obligatoire")
    @DecimalMin(value = "8.0", message = "Destination hors zone desservie")
    @DecimalMax(value = "16.5", message = "Destination hors zone desservie")
    private BigDecimal arriveeLng;

    // Ignoré : la ville est déterminée côté serveur à partir du point de départ.
    // Conservé pour compatibilité avec les anciens clients.
    private Long villeId;
}

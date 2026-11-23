package auth_service.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PointCampusDTO {
    @NotNull(message = "Ville obligatoire")
    private Long villeId;

    @NotBlank(message = "Nom obligatoire")
    @Size(max = 150)
    private String nom;

    @Size(max = 60)
    private String categorie;

    @Size(max = 255)
    private String adresse;

    @NotNull(message = "Latitude obligatoire")
    @DecimalMin("-90") @DecimalMax("90")
    private BigDecimal lat;

    @NotNull(message = "Longitude obligatoire")
    @DecimalMin("-180") @DecimalMax("180")
    private BigDecimal lng;

    private Boolean actif = true;
}

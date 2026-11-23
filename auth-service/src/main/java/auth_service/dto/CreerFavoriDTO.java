package auth_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class CreerFavoriDTO {

    @NotBlank(message = "Le label est obligatoire")
    private String label;

    @NotBlank(message = "L'adresse est obligatoire")
    private String adresse;

    private BigDecimal lat;
    private BigDecimal lng;
}
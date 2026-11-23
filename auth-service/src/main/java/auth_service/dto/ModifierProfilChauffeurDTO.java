package auth_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ModifierProfilChauffeurDTO {
    @NotBlank(message = "Téléphone obligatoire")
    @Pattern(regexp = "^(\\+?237)?6\\d{8}$", message = "Numéro camerounais invalide (ex : 6XXXXXXXX)")
    private String telephone;

    // Numéros Mobile Money sur lesquels le passager paie le chauffeur (facultatifs)
    @Pattern(regexp = "^$|^(\\+?237)?6\\d{8}$", message = "Numéro MTN invalide")
    private String numeroMtn;

    @Pattern(regexp = "^$|^(\\+?237)?6\\d{8}$", message = "Numéro Orange invalide")
    private String numeroOrange;
}

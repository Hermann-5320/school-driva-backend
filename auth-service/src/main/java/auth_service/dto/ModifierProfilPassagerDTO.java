package auth_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ModifierProfilPassagerDTO {
    @NotBlank(message = "Nom obligatoire")
    @Size(max = 100)
    private String nom;

    @NotBlank(message = "Prénom obligatoire")
    @Size(max = 100)
    private String prenom;

    @NotBlank(message = "Téléphone obligatoire")
    @Pattern(regexp = "^(\\+?237)?6\\d{8}$", message = "Numéro camerounais invalide (ex : 6XXXXXXXX)")
    private String telephone;

    private Long villeId;
}

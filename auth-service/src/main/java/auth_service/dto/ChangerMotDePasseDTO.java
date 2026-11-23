package auth_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangerMotDePasseDTO {
    @NotBlank(message = "Mot de passe actuel obligatoire")
    private String ancienMotDePasse;

    @NotBlank(message = "Nouveau mot de passe obligatoire")
    @Size(min = 8, max = 100, message = "Le nouveau mot de passe doit faire au moins 8 caractères")
    private String nouveauMotDePasse;
}

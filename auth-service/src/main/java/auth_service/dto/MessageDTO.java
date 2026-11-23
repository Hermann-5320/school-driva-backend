package auth_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MessageDTO {
    @NotBlank(message = "Sujet obligatoire")
    @Size(max = 150)
    private String sujet;

    @NotBlank(message = "Message obligatoire")
    @Size(max = 3000)
    private String texte;
}

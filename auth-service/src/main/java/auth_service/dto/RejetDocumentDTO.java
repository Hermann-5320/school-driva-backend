package auth_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RejetDocumentDTO {
    @NotBlank(message = "La raison du rejet est obligatoire")
    private String raison;
}
package auth_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Action d'administration réalisée dans un autre service (course, paiement). */
@Data
public class JournalInterneDTO {
    @NotBlank
    private String email;
    @NotBlank
    private String action;
    private String entite;
    private Long entiteId;
}

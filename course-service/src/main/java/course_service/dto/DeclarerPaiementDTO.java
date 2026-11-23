package course_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class DeclarerPaiementDTO {
    @NotBlank(message = "Le mode de paiement est obligatoire")
    @Pattern(regexp = "(?i)^(LIQUIDE|MTN|ORANGE)$", message = "Mode : LIQUIDE, MTN ou ORANGE")
    private String mode;
}

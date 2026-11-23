package course_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class StatutCourseDTO {
    @NotBlank(message = "Statut obligatoire")
    @Pattern(regexp = "^(EN_ROUTE|ARRIVEE|DEMARREE|TERMINEE)$", message = "Statut invalide")
    private String statut;
}

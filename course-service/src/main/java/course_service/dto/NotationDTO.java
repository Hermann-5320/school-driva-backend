package course_service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class NotationDTO {

    @NotNull(message = "Note obligatoire")
    @Min(value = 1, message = "Note minimum 1")
    @Max(value = 5, message = "Note maximum 5")
    private Integer note;

    @jakarta.validation.constraints.Size(max = 1000, message = "Commentaire trop long")
    private String commentaire;
    private Boolean securiteOk;
    @jakarta.validation.constraints.Size(max = 500)
    private String tags;
}
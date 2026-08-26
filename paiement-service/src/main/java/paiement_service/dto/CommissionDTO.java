package paiement_service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class CommissionDTO {

    @NotNull(message = "L'id du chauffeur est obligatoire")
    private Long chauffeurId;

    @NotNull(message = "Le montant de la course est obligatoire")
    @Positive(message = "Le montant doit être positif")
    private BigDecimal montantCourse;

    private Long courseId;
}
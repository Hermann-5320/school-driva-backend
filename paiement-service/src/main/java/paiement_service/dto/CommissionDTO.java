package paiement_service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class CommissionDTO {

    @NotNull(message = "L'id du chauffeur est obligatoire")
    private Long chauffeurId;

    @NotNull(message = "Le montant de la course est obligatoire")
    @Positive(message = "Le montant doit être positif")
    private BigDecimal montantCourse;

    // Commission calculée par course-service (paramètre admin). Si absente,
    // paiement-service applique le taux par défaut.
    @PositiveOrZero(message = "La commission ne peut pas être négative")
    private BigDecimal commission;

    private Long courseId;
}
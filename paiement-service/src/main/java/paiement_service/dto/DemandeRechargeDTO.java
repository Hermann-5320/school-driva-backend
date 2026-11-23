package paiement_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class DemandeRechargeDTO {

    @NotNull(message = "Le montant est obligatoire")
    @DecimalMin(value = "100", message = "Recharge minimum : 100 FCFA")
    @DecimalMax(value = "500000", message = "Recharge maximum : 500 000 FCFA")
    private BigDecimal montant;

    @NotBlank(message = "Le numéro de téléphone est obligatoire")
    @Pattern(regexp = "^(\\+?237)?6\\d{8}$", message = "Numéro camerounais invalide (ex : 6XXXXXXXX)")
    private String telephone;

    @NotBlank(message = "L'opérateur est obligatoire")
    @Pattern(regexp = "(?i)^(MTN|ORANGE)$", message = "Opérateur : MTN ou ORANGE")
    private String operateur; // MTN ou ORANGE
}
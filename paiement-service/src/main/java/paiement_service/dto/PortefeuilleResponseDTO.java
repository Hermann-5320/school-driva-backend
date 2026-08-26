package paiement_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class PortefeuilleResponseDTO {
    private Long chauffeurId;
    private BigDecimal solde;
    private Boolean soldeInsuffisant; // true si en dessous du seuil
}
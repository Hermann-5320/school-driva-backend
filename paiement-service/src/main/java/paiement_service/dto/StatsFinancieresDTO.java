package paiement_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class StatsFinancieresDTO {
    private BigDecimal totalCommissions;
    private BigDecimal totalRecharges;
    private Long nbRechargesEnAttente;
    private BigDecimal soldeCumuleChauffeurs;
}
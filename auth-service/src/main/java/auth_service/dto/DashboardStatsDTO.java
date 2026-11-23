package auth_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DashboardStatsDTO {
    private Long nbChauffeursTotal;
    private Long nbChauffeursActifs;
    private Long nbChauffeursEnAttente;
    private Long nbChauffeursBloques;
    private Long nbChauffeursEnLigne;
    private Long nbPassagersTotal;
    private Long nbPassagersActifs;
    private Long nbPassagersBloques;
    private Long nbDocumentsEnAttente;
}
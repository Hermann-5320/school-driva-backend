package auth_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class DocumentAdminDTO {
    private Long id;
    private Long chauffeurId;
    private String chauffeurNom;
    private String type;
    private String statut;
    private String chemin;
    private String raisonRejet;
    private LocalDateTime updatedAt;
}
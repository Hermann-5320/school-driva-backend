package auth_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class ChauffeurDisponibleDTO {
    private Long id;
    private String nom;
    private String prenom;
    private String telephone;
    private BigDecimal noteMoyenne;
    private String villeNom;
    // VOITURE ou MOTO : le passager voit la disponibilité par type
    private String typeVehicule;
}
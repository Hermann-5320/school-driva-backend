package auth_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ChauffeurAdminDTO {
    private Long id;
    private Long utilisateurId;
    private String nom;
    private String prenom;
    private String email;
    private String telephone;
    private Long villeId;
    private String villeNom;
    private String statut;
    private Boolean enLigne;
    private BigDecimal noteMoyenne;
    private Integer nbCourses;
    private LocalDateTime createdAt;
    // Véhicule
    private String typeVehicule;
    private String marque;
    private String modele;
    private String couleur;
    private String immatriculation;
    // Documents
    private long docsValides;
    private long docsEnAttente;
    private long docsRejetes;
}

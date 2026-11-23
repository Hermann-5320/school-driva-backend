package auth_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class ChauffeurProfilDTO {
    private Long id;
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
    private BigDecimal kmTotal;
    private String numeroMtn;
    private String numeroOrange;
    private String typeVehicule;
    private String marque;
    private String modele;
    private String couleur;
    private String immatriculation;
}

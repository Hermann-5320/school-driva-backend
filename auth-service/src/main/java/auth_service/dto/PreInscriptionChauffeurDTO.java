package auth_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PreInscriptionChauffeurDTO {

    @NotBlank(message = "Nom obligatoire")
    private String nom;

    @NotBlank(message = "Prénom obligatoire")
    private String prenom;

    @Email(message = "Email invalide")
    @NotBlank(message = "Email obligatoire")
    private String email;

    @NotBlank(message = "Téléphone obligatoire")
    private String telephone;

    private Long villeId;

    // Véhicule
    @NotBlank(message = "Type de véhicule obligatoire")
    private String typeVehicule;

    private String marque;
    private String modele;
    private String couleur;
    private Integer annee;

    @NotBlank(message = "Immatriculation obligatoire")
    private String immatriculation;
}
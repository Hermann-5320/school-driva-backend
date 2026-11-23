package auth_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class PassagerProfilDTO {
    private Long id;
    private String nom;
    private String prenom;
    private String email;
    private String telephone;
    private Long villeId;
    private String villeNom;
    private String statut;
    private LocalDateTime createdAt;
}

package auth_service.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "utilisateurs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Utilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @JsonIgnore
    @Column(name = "mot_de_passe", nullable = false)
    private String motDePasse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    private Statut statut = Statut.ACTIF;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum Role {
        PASSAGER, CHAUFFEUR, ADMIN
    }

    public enum Statut {
        ACTIF, BLOQUE
    }
    @JsonIgnore
    @Column(name = "tentatives_echouees")
    private Integer tentativesEchouees = 0;

    @JsonIgnore
    @Column(name = "verrouille_jusqu_a")
    private LocalDateTime verrouilleJusquA;
    @JsonIgnore
    @Column(name = "code_reset")
    private String codeReset;

    @JsonIgnore
    @Column(name = "code_reset_expire")
    private LocalDateTime codeResetExpire;

    // Vrai tant que l'utilisateur utilise un mot de passe temporaire (chauffeur validé)
    @Column(name = "doit_changer_mdp")
    private Boolean doitChangerMotDePasse = false;
}

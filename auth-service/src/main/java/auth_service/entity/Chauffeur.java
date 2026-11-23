package auth_service.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "chauffeurs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Chauffeur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "utilisateur_id", unique = true, nullable = false)
    private Utilisateur utilisateur;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String prenom;

    @Column(nullable = false)
    private String telephone;

    @ManyToOne
    @JoinColumn(name = "ville_id")
    private Ville ville;

    @Column(name = "numero_mtn")
    private String numeroMtn;

    @Column(name = "numero_orange")
    private String numeroOrange;

    @Enumerated(EnumType.STRING)
    private Statut statut = Statut.EN_ATTENTE;

    @Column(name = "note_moyenne")
    private BigDecimal noteMoyenne = BigDecimal.valueOf(0.0);

    @Column(name = "nb_courses")
    private Integer nbCourses = 0;

    @Column(name = "km_total")
    private BigDecimal kmTotal = BigDecimal.valueOf(0.0);

    @Column(name = "en_ligne")
    private Boolean enLigne = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum Statut {
        EN_ATTENTE, ACTIF, BLOQUE
    }
}
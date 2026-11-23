package auth_service.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Point de prise en charge / destination pré-enregistré (campus, cité U...). */
@Entity
@Table(name = "points_campus")
@Data
@NoArgsConstructor
public class PointCampus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ville_id", nullable = false)
    private Long villeId;

    @Column(nullable = false)
    private String nom;

    // ex : "Université", "Grande école", "Cité universitaire"
    private String categorie;

    private String adresse;

    @Column(nullable = false)
    private BigDecimal lat;

    @Column(nullable = false)
    private BigDecimal lng;

    private Boolean actif = true;
}

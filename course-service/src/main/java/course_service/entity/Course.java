package course_service.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "courses")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "passager_id", nullable = false)
    private Long passagerId;

    @Column(name = "chauffeur_id")
    private Long chauffeurId;

    @Column(name = "ville_id")
    private Long villeId;

    @Column(name = "type_vehicule", nullable = false)
    private String typeVehicule;

    @Column(name = "depart_adresse", nullable = false)
    private String departAdresse;

    @Column(name = "depart_lat")
    private BigDecimal departLat;

    @Column(name = "depart_lng")
    private BigDecimal departLng;

    @Column(name = "arrivee_adresse", nullable = false)
    private String arriveeAdresse;

    @Column(name = "arrivee_lat")
    private BigDecimal arriveeLat;

    @Column(name = "arrivee_lng")
    private BigDecimal arriveeLng;

    @Column(name = "distance_km")
    private BigDecimal distanceKm;

    @Column(name = "duree_min")
    private Integer dureeMin;

    @Column(name = "prix_estime")
    private BigDecimal prixEstime;

    @Column(name = "prix_final")
    private BigDecimal prixFinal;

    @Column(nullable = false)
    private String statut = "EN_ATTENTE";

    @Column(name = "annulee_par")
    private String annuleePar;

    // Jeton du lien de suivi partagé : jamais renvoyé dans le JSON de la course
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "partage_token")
    private String partageToken;

    // Dernière position connue du chauffeur (suivi en temps réel)
    @Column(name = "chauffeur_lat")
    private BigDecimal chauffeurLat;

    @Column(name = "chauffeur_lng")
    private BigDecimal chauffeurLng;

    @Column(name = "position_maj_at")
    private LocalDateTime positionMajAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;
}
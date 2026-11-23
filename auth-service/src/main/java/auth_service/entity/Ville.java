package auth_service.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "villes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Ville {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom;

    private String region;

    private Boolean active = true;

    // Centre de la ville : sert à rattacher une course à la ville desservie
    // la plus proche de son point de départ
    @Column(precision = 10, scale = 8)
    private java.math.BigDecimal lat;

    @Column(precision = 11, scale = 8)
    private java.math.BigDecimal lng;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
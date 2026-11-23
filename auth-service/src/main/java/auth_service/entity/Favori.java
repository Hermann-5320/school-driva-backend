package auth_service.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "favoris")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Favori {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "passager_id", nullable = false)
    private Long passagerId;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private String adresse;

    private BigDecimal lat;
    private BigDecimal lng;
}
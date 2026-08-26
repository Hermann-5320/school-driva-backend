package paiement_service.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "portefeuilles")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Portefeuille {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chauffeur_id", unique = true, nullable = false)
    private Long chauffeurId;

    @Column(nullable = false)
    private BigDecimal solde = BigDecimal.ZERO;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
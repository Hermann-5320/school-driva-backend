package paiement_service.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "portefeuille_id", nullable = false)
    private Long portefeuilleId;

    @Column(nullable = false)
    private String type; // COMMISSION ou RECHARGE

    @Column(nullable = false)
    private BigDecimal montant;

    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "recharge_id")
    private Long rechargeId;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
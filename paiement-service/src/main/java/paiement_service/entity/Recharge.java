package paiement_service.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "recharges")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Recharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chauffeur_id", nullable = false)
    private Long chauffeurId;

    @Column(nullable = false)
    private BigDecimal montant;

    @Column(nullable = false)
    private String operateur; // MTN ou ORANGE

    @Column(nullable = false)
    private String statut = "EN_ATTENTE"; // EN_ATTENTE, VALIDEE, REJETEE

    @Column(name = "pay_token")
    private String payToken; // Token retourné par AangaraaPay

    @Column(name = "reference_transaction")
    private String referenceTransaction;

    @Column(name = "raison_rejet")
    private String raisonRejet;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;
}
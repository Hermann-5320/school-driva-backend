package paiement_service.repository;

import paiement_service.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByPortefeuilleIdOrderByCreatedAtDesc(Long portefeuilleId);

    @Query("SELECT COALESCE(SUM(t.montant), 0) FROM Transaction t WHERE t.type = 'COMMISSION'")
    BigDecimal getTotalCommissions();

    @Query("SELECT COALESCE(SUM(t.montant), 0) FROM Transaction t WHERE t.type = 'RECHARGE'")
    BigDecimal getTotalRecharges();
    boolean existsByCourseIdAndType(Long courseId, String type);

    List<Transaction> findTop200ByOrderByCreatedAtDesc();
}

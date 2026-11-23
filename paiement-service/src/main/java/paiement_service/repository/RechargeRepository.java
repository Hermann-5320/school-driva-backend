package paiement_service.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import paiement_service.entity.Recharge;

import java.util.List;
import java.util.Optional;

@Repository
public interface RechargeRepository extends JpaRepository<Recharge, Long> {

    List<Recharge> findByChauffeurIdOrderByCreatedAtDesc(Long chauffeurId);

    Optional<Recharge> findByPayToken(String payToken);

    Optional<Recharge> findByReferenceTransaction(String referenceTransaction);

    List<Recharge> findByStatut(String statut);

    Long countByStatut(String statut);

    List<Recharge> findAllByOrderByCreatedAtDesc();

    // Verrous ligne (SELECT ... FOR UPDATE) : deux notifications simultanées pour
    // la même recharge sont traitées l'une après l'autre → jamais de double crédit.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Recharge r WHERE r.payToken = :payToken")
    Optional<Recharge> findByPayTokenPourMaj(@Param("payToken") String payToken);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Recharge r WHERE r.referenceTransaction = :ref")
    Optional<Recharge> findByReferenceTransactionPourMaj(@Param("ref") String ref);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Recharge r WHERE r.id = :id")
    Optional<Recharge> findByIdPourMaj(@Param("id") Long id);
}

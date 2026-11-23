package paiement_service.repository;

import paiement_service.entity.Portefeuille;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface PortefeuilleRepository extends JpaRepository<Portefeuille, Long> {
    Optional<Portefeuille> findByChauffeurId(Long chauffeurId);

    @Query("SELECT COALESCE(SUM(p.solde), 0) FROM Portefeuille p")
    BigDecimal getSoldeCumule();
}
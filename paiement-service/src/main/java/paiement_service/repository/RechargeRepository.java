package paiement_service.repository;

import paiement_service.entity.Recharge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface RechargeRepository extends JpaRepository<Recharge, Long> {
    List<Recharge> findByChauffeurIdOrderByCreatedAtDesc(Long chauffeurId);
    Optional<Recharge> findByPayToken(String payToken);
    List<Recharge> findByStatut(String statut);
}
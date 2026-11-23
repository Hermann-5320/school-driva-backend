package course_service.repository;

import course_service.entity.Paiement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaiementRepository extends JpaRepository<Paiement, Long> {
    Optional<Paiement> findByCourseId(Long courseId);
    List<Paiement> findByCourseIdIn(Collection<Long> courseIds);
}

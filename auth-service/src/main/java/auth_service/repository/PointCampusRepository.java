package auth_service.repository;

import auth_service.entity.PointCampus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PointCampusRepository extends JpaRepository<PointCampus, Long> {
    List<PointCampus> findByVilleIdAndActifTrueOrderByNomAsc(Long villeId);
    List<PointCampus> findByActifTrueOrderByNomAsc();
    List<PointCampus> findAllByOrderByNomAsc();
}

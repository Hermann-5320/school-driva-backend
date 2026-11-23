package auth_service.repository;

import auth_service.entity.Ville;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface VilleRepository extends JpaRepository<Ville, Long> {
    List<Ville> findByActiveTrue();
}
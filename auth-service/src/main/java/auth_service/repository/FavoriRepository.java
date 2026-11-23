package auth_service.repository;

import auth_service.entity.Favori;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface FavoriRepository extends JpaRepository<Favori, Long> {
    List<Favori> findByPassagerId(Long passagerId);
}


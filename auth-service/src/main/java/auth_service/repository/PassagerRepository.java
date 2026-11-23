package auth_service.repository;

import auth_service.entity.Passager;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import auth_service.entity.Utilisateur;
import org.springframework.data.jpa.repository.Query;

@Repository
public interface PassagerRepository extends JpaRepository<Passager, Long> {
    Optional<Passager> findByUtilisateurId(Long utilisateurId);
    @Query("SELECT COUNT(p) FROM Passager p WHERE p.utilisateur.statut = :statut")
    Long countByUtilisateurStatut(Utilisateur.Statut statut);
}
package auth_service.repository;

import auth_service.entity.Chauffeur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChauffeurRepository extends JpaRepository<Chauffeur, Long> {
    Optional<Chauffeur> findByUtilisateurId(Long utilisateurId);
    List<Chauffeur> findByVille_IdAndEnLigneTrueAndStatut(Long villeId, Chauffeur.Statut statut);
    List<Chauffeur> findByStatut(Chauffeur.Statut statut);
    Long countByStatut(Chauffeur.Statut statut);
    Long countByEnLigneTrue();
}
package auth_service.repository;

import auth_service.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByChauffeur_Id(Long chauffeurId);
    Optional<Document> findByChauffeur_IdAndType(Long chauffeurId, String type);
    List<Document> findByStatut(String statut);
    Long countByStatut(String statut);
    long countByChauffeur_IdAndStatut(Long chauffeurId, String statut);
}

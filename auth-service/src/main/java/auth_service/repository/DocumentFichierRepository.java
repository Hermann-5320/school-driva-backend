package auth_service.repository;

import auth_service.entity.DocumentFichier;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentFichierRepository extends JpaRepository<DocumentFichier, Long> {
}

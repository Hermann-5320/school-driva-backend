package auth_service.repository;

import auth_service.entity.JournalAdmin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JournalAdminRepository extends JpaRepository<JournalAdmin, Long> {
    List<JournalAdmin> findTop50ByOrderByCreatedAtDesc();
}

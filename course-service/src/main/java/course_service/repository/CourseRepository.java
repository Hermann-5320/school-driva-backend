package course_service.repository;

import course_service.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    List<Course> findByPassagerIdOrderByCreatedAtDesc(Long passagerId);

    List<Course> findByChauffeurIdOrderByCreatedAtDesc(Long chauffeurId);

    List<Course> findByStatutAndVilleIdOrderByCreatedAtAsc(String statut, Long villeId);

    List<Course> findByStatutAndVilleIdAndTypeVehiculeOrderByCreatedAtAsc(String statut, Long villeId, String typeVehicule);

    List<Course> findByChauffeurIdAndStatutIn(Long chauffeurId, List<String> statuts);

    List<Course> findByPassagerIdAndStatutIn(Long passagerId, List<String> statuts);

    Long countByChauffeurIdAndStatut(Long chauffeurId, String statut);

    List<Course> findAllByOrderByCreatedAtDesc();

    List<Course> findByStatutIn(List<String> statuts);

    boolean existsByPassagerIdAndStatutIn(Long passagerId, List<String> statuts);

    Optional<Course> findByPartageToken(String partageToken);

    List<Course> findByStatutAndCreatedAtBefore(String statut, LocalDateTime limite);

    // Acceptation atomique : un seul UPDATE conditionnel. Si deux chauffeurs
    // acceptent en même temps, un seul obtient « 1 ligne modifiée ».
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Course c SET c.chauffeurId = :chauffeurId, c.statut = 'ACCEPTEE' " +
           "WHERE c.id = :courseId AND c.statut = 'EN_ATTENTE'")
    int accepterSiDisponible(@Param("courseId") Long courseId, @Param("chauffeurId") Long chauffeurId);
}

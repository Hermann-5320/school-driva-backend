package auth_service.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "journal_admin")
@Data
@NoArgsConstructor
public class JournalAdmin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Column(nullable = false, length = 500)
    private String action;

    private String entite;

    @Column(name = "entite_id")
    private Long entiteId;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}

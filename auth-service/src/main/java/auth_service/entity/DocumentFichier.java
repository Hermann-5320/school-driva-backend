package auth_service.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Contenu binaire d'un document, dans une table séparée pour ne pas le
 * charger à chaque lecture de la liste des documents. Stocké en base car
 * le disque des instances Render gratuites est effacé à chaque redémarrage.
 */
@Entity
@Table(name = "document_fichiers")
@Data
@NoArgsConstructor
public class DocumentFichier {

    @Id
    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "contenu", nullable = false, columnDefinition = "bytea")
    private byte[] contenu;
}

package auth_service.service;

import auth_service.entity.Chauffeur;
import auth_service.entity.Document;
import auth_service.entity.DocumentFichier;
import auth_service.repository.ChauffeurRepository;
import auth_service.repository.DocumentFichierRepository;
import auth_service.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentFichierRepository documentFichierRepository;
    private final ChauffeurRepository chauffeurRepository;

    public static final List<String> TYPES_AUTORISES = List.of(
            "CNI_RECTO", "CNI_VERSO", "SELFIE_CNI",
            "PERMIS", "PLAN_DOMICILE", "CARTE_GRISE",
            "ASSURANCE", "VISITE_TECHNIQUE",
            "PHOTOS_VEHICULE", "CNI_GARANT", "CONTACTS_GARANT"
    );

    // Documents indispensables avant de pouvoir valider un chauffeur
    public static final List<String> TYPES_OBLIGATOIRES = List.of(
            "CNI_RECTO", "CNI_VERSO", "PERMIS", "CARTE_GRISE"
    );

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "pdf", "application/pdf",
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg"
    );

    private static final long TAILLE_MAX = 5L * 1024 * 1024;

    @Transactional
    public void uploaderDocument(Long chauffeurId, String type, MultipartFile fichier) {
        String typeNormalise = type == null ? "" : type.toUpperCase();
        if (!TYPES_AUTORISES.contains(typeNormalise)) {
            throw new RuntimeException("Type de document invalide : " + type);
        }

        Chauffeur chauffeur = chauffeurRepository.findById(chauffeurId)
                .orElseThrow(() -> new RuntimeException("Chauffeur introuvable"));

        String nomFichier = fichier.getOriginalFilename();
        if (nomFichier == null || !nomFichier.contains(".")) {
            throw new RuntimeException("Nom de fichier invalide");
        }
        String extension = nomFichier.substring(nomFichier.lastIndexOf('.') + 1).toLowerCase();
        if (!CONTENT_TYPES.containsKey(extension)) {
            throw new RuntimeException("Format non autorisé. Utilisez JPG, PNG ou PDF.");
        }
        if (fichier.getSize() > TAILLE_MAX) {
            throw new RuntimeException("Fichier trop volumineux. Maximum 5 Mo.");
        }

        byte[] contenu;
        try {
            contenu = fichier.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("Impossible de lire le fichier envoyé");
        }

        // Contenu réel (signature binaire) : un script renommé en .pdf est rejeté
        if (!contenuCorrespond(contenu, extension)) {
            throw new RuntimeException("Le contenu du fichier ne correspond pas à son format.");
        }

        Document document = documentRepository
                .findByChauffeur_IdAndType(chauffeurId, typeNormalise)
                .orElse(new Document());
        document.setChauffeur(chauffeur);
        document.setType(typeNormalise);
        document.setStatut("EN_ATTENTE");
        document.setRaisonRejet(null);
        document.setChemin(null);
        document.setUpdatedAt(LocalDateTime.now());
        document = documentRepository.save(document);

        DocumentFichier stockage = documentFichierRepository.findById(document.getId())
                .orElse(new DocumentFichier());
        stockage.setDocumentId(document.getId());
        stockage.setContentType(CONTENT_TYPES.get(extension));
        stockage.setContenu(contenu);
        documentFichierRepository.save(stockage);
    }

    static boolean contenuCorrespond(byte[] c, String extension) {
        if (c == null || c.length < 4) return false;
        int b0 = c[0] & 0xFF, b1 = c[1] & 0xFF, b2 = c[2] & 0xFF, b3 = c[3] & 0xFF;
        return switch (extension) {
            case "pdf" -> b0 == 0x25 && b1 == 0x50 && b2 == 0x44 && b3 == 0x46;   // %PDF
            case "png" -> b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47;   // .PNG
            case "jpg", "jpeg" -> b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF;        // JPEG
            default -> false;
        };
    }

    public List<Document> getDocumentsChauffeur(Long chauffeurId) {
        return documentRepository.findByChauffeur_Id(chauffeurId);
    }

    /** Liste des documents obligatoires manquants ou non validés. */
    public List<String> documentsObligatoiresNonValides(Long chauffeurId) {
        List<Document> docs = documentRepository.findByChauffeur_Id(chauffeurId);
        return TYPES_OBLIGATOIRES.stream()
                .filter(t -> docs.stream().noneMatch(d -> d.getType().equals(t) && "VALIDE".equals(d.getStatut())))
                .toList();
    }
}

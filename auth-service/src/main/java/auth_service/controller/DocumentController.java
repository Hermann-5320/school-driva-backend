package auth_service.controller;

import auth_service.dto.DocumentAdminDTO;
import auth_service.dto.RejetDocumentDTO;
import auth_service.entity.Chauffeur;
import auth_service.entity.Document;
import auth_service.entity.DocumentFichier;
import auth_service.entity.Utilisateur;
import auth_service.repository.ChauffeurRepository;
import auth_service.repository.DocumentFichierRepository;
import auth_service.repository.DocumentRepository;
import auth_service.security.JwtService;
import auth_service.security.UtilisateurCourant;
import auth_service.service.DocumentService;
import auth_service.service.DossierChauffeurService;
import auth_service.service.JournalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chauffeur/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentRepository documentRepository;
    private final DocumentFichierRepository documentFichierRepository;
    private final ChauffeurRepository chauffeurRepository;
    private final JwtService jwtService;
    private final UtilisateurCourant utilisateurCourant;
    private final JournalService journalService;
    private final DossierChauffeurService dossierChauffeurService;

    // POST /api/chauffeur/documents/upload — deux cas autorisés :
    //  1. dossier pas encore validé : X-Upload-Token (48 h) pour CE chauffeur ;
    //  2. chauffeur connecté qui renvoie SES propres documents.
    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploaderDocument(
            @RequestParam("chauffeurId") Long chauffeurId,
            @RequestParam("type") String type,
            @RequestParam("fichier") MultipartFile fichier,
            @RequestHeader(value = "X-Upload-Token", required = false) String uploadToken) {

        if (!peutUploader(chauffeurId, uploadToken)) {
            throw new AccessDeniedException("Vous n'êtes pas autorisé à envoyer des documents pour ce chauffeur");
        }
        if (fichier.isEmpty()) {
            throw new RuntimeException("Fichier vide");
        }
        documentService.uploaderDocument(chauffeurId, type, fichier);
        return ResponseEntity.ok(Map.of("message", "Document " + type + " soumis avec succès"));
    }

    private boolean peutUploader(Long chauffeurId, String uploadToken) {
        if (uploadToken != null && !uploadToken.isBlank()) {
            Long idDuJeton = jwtService.extraireChauffeurIdUpload(uploadToken);
            return idDuJeton != null && idDuJeton.equals(chauffeurId)
                    && chauffeurRepository.findById(chauffeurId)
                    .map(c -> c.getStatut() == Chauffeur.Statut.EN_ATTENTE).orElse(false);
        }
        if (utilisateurCourant.estConnecte()) {
            Utilisateur u = utilisateurCourant.get();
            return u.getRole() == Utilisateur.Role.CHAUFFEUR
                    && chauffeurRepository.findByUtilisateurId(u.getId())
                    .map(c -> c.getId().equals(chauffeurId)).orElse(false);
        }
        return false;
    }

    // ── Compléter son dossier plus tard (PUBLIC) ──────────

    // Demande du lien personnel par email. Réponse identique que l'email
    // existe ou non (pas d'énumération des comptes).
    @PostMapping("/dossier/lien")
    public ResponseEntity<Map<String, String>> demanderLienDossier(@RequestBody Map<String, String> body) {
        dossierChauffeurService.envoyerLienSiEligible(body == null ? null : body.get("email"));
        return ResponseEntity.ok(Map.of("message",
                "Si un dossier chauffeur en attente correspond à cet email, un lien vient de vous être envoyé."));
    }

    // État du dossier, consulté avec le jeton reçu par email
    @GetMapping("/dossier")
    public ResponseEntity<Map<String, Object>> etatDossier(
            @RequestHeader(value = "X-Upload-Token", required = false) String uploadToken) {
        Long chauffeurId = uploadToken == null ? null : jwtService.extraireChauffeurIdUpload(uploadToken);
        if (chauffeurId == null) {
            throw new AccessDeniedException("Lien invalide ou expiré. Demandez-en un nouveau.");
        }
        Chauffeur chauffeur = chauffeurRepository.findById(chauffeurId)
                .orElseThrow(() -> new AccessDeniedException("Lien invalide ou expiré. Demandez-en un nouveau."));
        if (chauffeur.getStatut() != Chauffeur.Statut.EN_ATTENTE) {
            throw new AccessDeniedException(chauffeur.getStatut() == Chauffeur.Statut.ACTIF
                    ? "Votre dossier est déjà validé : connectez-vous à votre espace chauffeur."
                    : "Ce dossier ne peut plus être modifié. Contactez le support.");
        }
        List<Map<String, Object>> documents = documentService.getDocumentsChauffeur(chauffeurId).stream()
                .map(d -> {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("type", d.getType());
                    m.put("statut", d.getStatut());
                    m.put("raisonRejet", d.getRaisonRejet());
                    return m;
                }).toList();
        Map<String, Object> reponse = new java.util.HashMap<>();
        reponse.put("chauffeurId", chauffeurId);
        reponse.put("prenom", chauffeur.getPrenom());
        reponse.put("documents", documents);
        reponse.put("obligatoires", DocumentService.TYPES_OBLIGATOIRES);
        return ResponseEntity.ok(reponse);
    }

    // Un chauffeur ne voit que ses documents ; un admin voit tout
    @GetMapping("/{chauffeurId}")
    public ResponseEntity<List<Document>> getDocuments(@PathVariable Long chauffeurId) {
        Utilisateur utilisateur = utilisateurCourant.get();
        if (utilisateur.getRole() != Utilisateur.Role.ADMIN) {
            Chauffeur chauffeur = chauffeurRepository.findByUtilisateurId(utilisateur.getId())
                    .orElseThrow(() -> new AccessDeniedException("Chauffeur introuvable"));
            if (!chauffeur.getId().equals(chauffeurId)) {
                throw new AccessDeniedException("Vous ne pouvez consulter que vos propres documents");
            }
        }
        return ResponseEntity.ok(documentService.getDocumentsChauffeur(chauffeurId));
    }

    @GetMapping("/admin/en-attente")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<DocumentAdminDTO>> documentsEnAttente() {
        return ResponseEntity.ok(documentRepository.findByStatut("EN_ATTENTE").stream()
                .map(d -> new DocumentAdminDTO(d.getId(), d.getChauffeur().getId(),
                        d.getChauffeur().getPrenom() + " " + d.getChauffeur().getNom(),
                        d.getType(), d.getStatut(), null, d.getRaisonRejet(), d.getUpdatedAt()))
                .toList());
    }

    @PutMapping("/admin/{documentId}/valider")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> validerDocument(@PathVariable Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document introuvable"));
        document.setStatut("VALIDE");
        document.setRaisonRejet(null);
        document.setUpdatedAt(LocalDateTime.now());
        documentRepository.save(document);
        journalService.log("Validation du document " + document.getType(), "DOCUMENT", documentId);
        return ResponseEntity.ok(Map.of("message", "Document validé"));
    }

    @PutMapping("/admin/{documentId}/rejeter")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> rejeterDocument(@PathVariable Long documentId,
                                                               @Valid @RequestBody RejetDocumentDTO dto) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document introuvable"));
        document.setStatut("REJETE");
        document.setRaisonRejet(dto.getRaison());
        document.setUpdatedAt(LocalDateTime.now());
        documentRepository.save(document);
        journalService.log("Rejet du document " + document.getType() + " : " + dto.getRaison(), "DOCUMENT", documentId);
        // Le chauffeur est prévenu par email, avec le moyen de renvoyer le document
        dossierChauffeurService.notifierRejet(document);
        return ResponseEntity.ok(Map.of("message", "Document rejeté"));
    }

    // Fichier d'un document (admin) — servi depuis la base
    @GetMapping("/admin/{documentId}/fichier")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> getFichierDocument(@PathVariable Long documentId) {
        DocumentFichier fichier = documentFichierRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Fichier introuvable pour ce document"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(fichier.getContentType()))
                // inline + nosniff : affichage dans le navigateur sans exécution
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("X-Content-Type-Options", "nosniff")
                .body(fichier.getContenu());
    }
}

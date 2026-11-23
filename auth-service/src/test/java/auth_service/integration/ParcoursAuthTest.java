package auth_service.integration;

import auth_service.entity.*;
import auth_service.repository.*;
import auth_service.security.JwtService;
import auth_service.service.EmailService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Parcours critiques d'authentification, de documents et d'administration. */
@SpringBootTest
@ActiveProfiles("test")
class ParcoursAuthTest {

    private static final byte[] PDF = "%PDF-1.4 contenu de test".getBytes();

    @Autowired WebApplicationContext contexte;
    @Autowired UtilisateurRepository utilisateurRepository;
    @Autowired PassagerRepository passagerRepository;
    @Autowired ChauffeurRepository chauffeurRepository;
    @Autowired VehiculeRepository vehiculeRepository;
    @Autowired DocumentRepository documentRepository;
    @Autowired DocumentFichierRepository documentFichierRepository;
    @Autowired FavoriRepository favoriRepository;
    @Autowired AdminRepository adminRepository;
    @Autowired JournalAdminRepository journalRepository;
    @Autowired PointCampusRepository pointCampusRepository;
    @Autowired VilleRepository villeRepository;
    @Autowired JwtService jwtService;
    @Autowired PasswordEncoder passwordEncoder;

    // Aucun email réel pendant les tests (le mock renvoie false = "non envoyé")
    @MockitoBean EmailService emailService;

    @Value("${app.internal-api-key}") String cleInterne;

    MockMvc mvc;

    @BeforeEach
    void preparer() {
        mvc = MockMvcBuilders.webAppContextSetup(contexte).apply(springSecurity()).build();
        journalRepository.deleteAll();
        adminRepository.deleteAll();
        favoriRepository.deleteAll();
        documentFichierRepository.deleteAll();
        documentRepository.deleteAll();
        vehiculeRepository.deleteAll();
        chauffeurRepository.deleteAll();
        passagerRepository.deleteAll();
        pointCampusRepository.deleteAll();
        utilisateurRepository.deleteAll();
    }

    // ── Outils ─────────────────────────────────────────────
    private Utilisateur utilisateur(String email, Utilisateur.Role role, String mdp) {
        Utilisateur u = new Utilisateur();
        u.setEmail(email);
        u.setMotDePasse(passwordEncoder.encode(mdp));
        u.setRole(role);
        return utilisateurRepository.save(u);
    }

    private Passager passager(String email) {
        Passager p = new Passager();
        p.setUtilisateur(utilisateur(email, Utilisateur.Role.PASSAGER, "motdepasse1"));
        p.setNom("TEST");
        p.setPrenom("Passager");
        p.setTelephone("690000000");
        return passagerRepository.save(p);
    }

    private Chauffeur chauffeur(String email, Chauffeur.Statut statut) {
        Utilisateur u = utilisateur(email, Utilisateur.Role.CHAUFFEUR, "motdepasse1");
        if (statut == Chauffeur.Statut.EN_ATTENTE) {
            u.setStatut(Utilisateur.Statut.BLOQUE);
            utilisateurRepository.save(u);
        }
        Chauffeur c = new Chauffeur();
        c.setUtilisateur(u);
        c.setNom("TEST");
        c.setPrenom("Chauffeur");
        c.setTelephone("670000000");
        c.setStatut(statut);
        return chauffeurRepository.save(c);
    }

    private String jeton(Utilisateur u) {
        return "Bearer " + jwtService.genererToken(u.getEmail(), u.getRole().name(), u.getId());
    }

    private String admin() {
        Utilisateur u = utilisateur("admin@test.cm", Utilisateur.Role.ADMIN, "motdepasse1");
        Admin a = new Admin();
        a.setUtilisateur(u);
        a.setNom("Admin Test");
        a.setRole("SUPER_ADMIN");
        adminRepository.save(a);
        return jeton(u);
    }

    // ── Tests ──────────────────────────────────────────────

    @Test
    void lesStatsChauffeurNeSontModifiablesQueParUnService() throws Exception {
        Chauffeur c = chauffeur("c@test.cm", Chauffeur.Statut.ACTIF);
        String corps = "{\"noteMoyenne\":5}";

        // Le chauffeur lui-même ne peut pas s'attribuer une note
        mvc.perform(put("/api/chauffeurs/" + c.getId() + "/stats").header("Authorization", jeton(c.getUtilisateur()))
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/chauffeurs/" + c.getId() + "/stats").header("X-Internal-Api-Key", cleInterne)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"noteMoyenne\":4.5}"))
                .andExpect(status().isOk());
        assertThat(chauffeurRepository.findById(c.getId()).orElseThrow().getNoteMoyenne())
                .isEqualByComparingTo(new BigDecimal("4.5"));
    }

    @Test
    void lUploadDeDocumentsExigeLeBonJeton() throws Exception {
        Chauffeur c1 = chauffeur("c1@test.cm", Chauffeur.Statut.EN_ATTENTE);
        Chauffeur c2 = chauffeur("c2@test.cm", Chauffeur.Statut.EN_ATTENTE);
        MockMultipartFile fichier = new MockMultipartFile("fichier", "permis.pdf", "application/pdf", PDF);

        // Sans jeton
        mvc.perform(multipart("/api/chauffeur/documents/upload").file(fichier)
                        .param("chauffeurId", c1.getId().toString()).param("type", "PERMIS"))
                .andExpect(status().isForbidden());

        // Jeton d'un autre chauffeur
        mvc.perform(multipart("/api/chauffeur/documents/upload").file(fichier)
                        .header("X-Upload-Token", jwtService.genererTokenUpload(c2.getId()))
                        .param("chauffeurId", c1.getId().toString()).param("type", "PERMIS"))
                .andExpect(status().isForbidden());

        // Bon jeton
        mvc.perform(multipart("/api/chauffeur/documents/upload").file(fichier)
                        .header("X-Upload-Token", jwtService.genererTokenUpload(c1.getId()))
                        .param("chauffeurId", c1.getId().toString()).param("type", "PERMIS"))
                .andExpect(status().isOk());
        assertThat(documentRepository.findByChauffeur_Id(c1.getId())).hasSize(1);
    }

    @Test
    void unFichierDeguiseEstRefuse() throws Exception {
        Chauffeur c = chauffeur("c@test.cm", Chauffeur.Statut.EN_ATTENTE);
        MockMultipartFile faux = new MockMultipartFile("fichier", "cni.pdf", "application/pdf",
                "#!/bin/sh\nrm -rf /".getBytes());
        mvc.perform(multipart("/api/chauffeur/documents/upload").file(faux)
                        .header("X-Upload-Token", jwtService.genererTokenUpload(c.getId()))
                        .param("chauffeurId", c.getId().toString()).param("type", "CNI_RECTO"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unCompteBloquePerdSonAccesImmediatement() throws Exception {
        Passager p = passager("p@test.cm");
        String jeton = jeton(p.getUtilisateur());
        mvc.perform(get("/api/passagers/mon-profil").header("Authorization", jeton)).andExpect(status().isOk());

        mvc.perform(put("/api/auth/admin/passagers/" + p.getId() + "/bloquer").header("Authorization", admin()))
                .andExpect(status().isOk());

        // Même jeton, encore valide, mais compte bloqué
        mvc.perform(get("/api/passagers/mon-profil").header("Authorization", jeton))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void unPassagerNePeutPasSupprimerLeFavoriDUnAutre() throws Exception {
        Passager a = passager("a@test.cm");
        Passager b = passager("b@test.cm");
        Favori f = new Favori();
        f.setPassagerId(a.getId());
        f.setLabel("Maison");
        f.setAdresse("Bastos");
        f = favoriRepository.save(f);

        mvc.perform(delete("/api/passagers/mes-favoris/" + f.getId()).header("Authorization", jeton(b.getUtilisateur())))
                .andExpect(status().isForbidden());
        assertThat(favoriRepository.findById(f.getId())).isPresent();
    }

    @Test
    void laValidationExigeLesDocumentsEtImposeUnNouveauMotDePasse() throws Exception {
        String admin = admin();
        Chauffeur c = chauffeur("nouveau@test.cm", Chauffeur.Statut.EN_ATTENTE);

        // Documents obligatoires absents → refus
        mvc.perform(put("/api/auth/admin/chauffeurs/" + c.getId() + "/valider").header("Authorization", admin))
                .andExpect(status().isBadRequest());

        for (String type : new String[]{"CNI_RECTO", "CNI_VERSO", "PERMIS", "CARTE_GRISE"}) {
            Document d = new Document();
            d.setChauffeur(c);
            d.setType(type);
            d.setStatut("VALIDE");
            documentRepository.save(d);
        }

        // Email non configuré (mock) → l'admin reçoit le mot de passe temporaire
        String reponse = mvc.perform(put("/api/auth/admin/chauffeurs/" + c.getId() + "/valider").header("Authorization", admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String mdpTemporaire = JsonPath.read(reponse, "$.motDePasseTemporaire");
        assertThat(mdpTemporaire).startsWith("SD-").hasSizeGreaterThanOrEqualTo(13);

        mvc.perform(post("/api/auth/connexion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nouveau@test.cm\",\"motDePasse\":\"" + mdpTemporaire + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doitChangerMotDePasse").value(true));

        // L'action est tracée dans le journal
        assertThat(journalRepository.findAll()).isNotEmpty();
    }

    @Test
    void laConnexionSeVerrouilleApresCinqEchecs() throws Exception {
        passager("p@test.cm");
        String mauvais = "{\"email\":\"p@test.cm\",\"motDePasse\":\"mauvais-mdp\"}";
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/connexion").contentType(MediaType.APPLICATION_JSON).content(mauvais))
                    .andExpect(status().isBadRequest());
        }
        // Même le bon mot de passe est refusé pendant le verrouillage
        mvc.perform(post("/api/auth/connexion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"p@test.cm\",\"motDePasse\":\"motdepasse1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("verrouillé")));
    }

    @Test
    void laDemandeDeResetNeRevelePasSiLEmailExiste() throws Exception {
        passager("p@test.cm");
        String r1 = mvc.perform(post("/api/auth/mot-de-passe/reset").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"p@test.cm\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String r2 = mvc.perform(post("/api/auth/mot-de-passe/reset").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"inconnu@test.cm\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(r1).isEqualTo(r2);
    }

    @Test
    void lesPointsCampusSontGeresParLAdminEtLusParLesPassagers() throws Exception {
        String admin = admin();
        Ville ville = new Ville();
        ville.setNom("Yaoundé");
        ville = villeRepository.save(ville);
        Passager p = passager("p@test.cm");

        String corps = "{\"villeId\":" + ville.getId() + ",\"nom\":\"Université de Yaoundé I\",\"categorie\":\"Université\","
                + "\"lat\":3.8589,\"lng\":11.4995}";
        mvc.perform(post("/api/admin/campus").header("Authorization", jeton(p.getUtilisateur()))
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/campus").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isOk());

        mvc.perform(get("/api/campus").param("villeId", ville.getId().toString())
                        .header("Authorization", jeton(p.getUtilisateur())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nom").value("Université de Yaoundé I"));
    }

    @Test
    void lesDonneesSensiblesNeSontJamaisSerialisees() throws Exception {
        Chauffeur c = chauffeur("c@test.cm", Chauffeur.Statut.ACTIF);
        Document d = new Document();
        d.setChauffeur(c);
        d.setType("PERMIS");
        d.setChemin("/srv/secret/permis.pdf");
        documentRepository.save(d);

        String json = mvc.perform(get("/api/chauffeur/documents/" + c.getId()).header("Authorization", jeton(c.getUtilisateur())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json).doesNotContain("motDePasse").doesNotContain("/srv/secret").doesNotContain("\"chauffeur\"");
    }

    // ── Compléter son dossier plus tard ────────────────────

    @Test
    void leDossierSeCompleteAvecLeLienPersonnel() throws Exception {
        Chauffeur c = chauffeur("dossier@test.cm", Chauffeur.Statut.EN_ATTENTE);
        String jeton = jwtService.genererTokenUpload(c.getId());

        // Sans jeton, ou avec un jeton bidon : refusé
        mvc.perform(get("/api/chauffeur/documents/dossier")).andExpect(status().isForbidden());
        mvc.perform(get("/api/chauffeur/documents/dossier").header("X-Upload-Token", "bidon"))
                .andExpect(status().isForbidden());

        // Avec le jeton : état du dossier, puis envoi d'un document manquant
        mvc.perform(get("/api/chauffeur/documents/dossier").header("X-Upload-Token", jeton))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prenom").value("Chauffeur"))
                .andExpect(jsonPath("$.documents").isEmpty());

        MockMultipartFile fichier = new MockMultipartFile("fichier", "permis.pdf", "application/pdf", PDF);
        mvc.perform(multipart("/api/chauffeur/documents/upload").file(fichier)
                        .header("X-Upload-Token", jeton)
                        .param("chauffeurId", c.getId().toString()).param("type", "PERMIS"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/chauffeur/documents/dossier").header("X-Upload-Token", jeton))
                .andExpect(jsonPath("$.documents[0].type").value("PERMIS"))
                .andExpect(jsonPath("$.documents[0].statut").value("EN_ATTENTE"));
    }

    @Test
    void leLienNeSertPlusUneFoisLeDossierValide() throws Exception {
        Chauffeur c = chauffeur("actif@test.cm", Chauffeur.Statut.ACTIF);
        mvc.perform(get("/api/chauffeur/documents/dossier")
                        .header("X-Upload-Token", jwtService.genererTokenUpload(c.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void laDemandeDeLienNeRevelePasSiLeCompteExiste() throws Exception {
        chauffeur("candidat@test.cm", Chauffeur.Statut.EN_ATTENTE);

        String r1 = mvc.perform(post("/api/chauffeur/documents/dossier/lien").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"candidat@test.cm\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String r2 = mvc.perform(post("/api/chauffeur/documents/dossier/lien").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"inconnu@test.cm\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(r1).isEqualTo(r2);

        verify(emailService).envoyerLienDossier(eq("candidat@test.cm"), any(), contains("/completer-dossier?token="));
        verify(emailService, never()).envoyerLienDossier(eq("inconnu@test.cm"), any(), any());
    }

    @Test
    void unDocumentRejetePrevientLeChauffeurAvecSonLien() throws Exception {
        String admin = admin();
        Chauffeur c = chauffeur("rejet@test.cm", Chauffeur.Statut.EN_ATTENTE);
        Document d = new Document();
        d.setChauffeur(c);
        d.setType("PERMIS");
        d = documentRepository.save(d);

        mvc.perform(put("/api/chauffeur/documents/admin/" + d.getId() + "/rejeter").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"raison\":\"Photo illisible\"}"))
                .andExpect(status().isOk());

        verify(emailService).envoyerDocumentRejete(eq("rejet@test.cm"), any(), eq("PERMIS"),
                eq("Photo illisible"), contains("/completer-dossier?token="));
    }
}

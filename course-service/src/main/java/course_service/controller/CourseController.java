package course_service.controller;

import course_service.client.AuthServiceClient;
import course_service.client.PaiementServiceClient;
import course_service.dto.*;
import course_service.entity.Course;
import course_service.entity.Notation;
import course_service.entity.Paiement;
import course_service.security.JwtService;
import course_service.service.CourseService;
import course_service.service.GeocodageService;
import course_service.service.ItineraireService;
import course_service.service.ParametreService;
import course_service.dto.PositionDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private static final Logger log = LoggerFactory.getLogger(CourseController.class);

    private final CourseService courseService;
    private final JwtService jwtService;
    private final AuthServiceClient authServiceClient;
    private final GeocodageService geocodageService;
    private final ItineraireService itineraireService;
    private final PaiementServiceClient paiementServiceClient;
    private final ParametreService parametreService;

    // ── Utilitaires d'identité ─────────────────────────────
    private Long passagerId(String token) {
        return authServiceClient.getPassagerId(jwtService.extraireUserId(token.substring(7)));
    }

    private Long chauffeurId(String token) {
        return authServiceClient.getChauffeurId(jwtService.extraireUserId(token.substring(7)));
    }

    /**
     * Vérifie que l'utilisateur a le droit de voir cette course :
     * admin → tout ; passager → ses courses ; chauffeur → ses courses,
     * ou une course encore EN_ATTENTE (pour pouvoir l'accepter).
     */
    private void verifierAccesCourse(Course course, String token) {
        String role = jwtService.extraireRole(token.substring(7));
        switch (role) {
            case "ADMIN" -> { }
            case "PASSAGER" -> {
                if (!course.getPassagerId().equals(passagerId(token))) {
                    throw new AccessDeniedException("Cette course ne vous appartient pas");
                }
            }
            case "CHAUFFEUR" -> {
                boolean enAttente = "EN_ATTENTE".equals(course.getStatut());
                if (!enAttente && (course.getChauffeurId() == null
                        || !course.getChauffeurId().equals(chauffeurId(token)))) {
                    throw new AccessDeniedException("Vous n'êtes pas le chauffeur de cette course");
                }
            }
            default -> throw new AccessDeniedException("Accès refusé");
        }
    }

    // ── PASSAGER ──────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasRole('PASSAGER')")
    public ResponseEntity<Course> creerCourse(@Valid @RequestBody CreerCourseDTO dto,
                                              @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(courseService.creerCourse(dto, passagerId(token)));
    }

    @GetMapping("/historique")
    @PreAuthorize("hasRole('PASSAGER')")
    public ResponseEntity<List<Course>> historiquePassager(@RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(courseService.getHistoriquePassager(passagerId(token)));
    }

    @PutMapping("/{id}/annuler")
    @PreAuthorize("hasRole('PASSAGER')")
    public ResponseEntity<Course> annulerCourse(@PathVariable Long id,
                                                @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(courseService.annulerCourse(id, passagerId(token), "PASSAGER"));
    }

    @PostMapping("/{id}/noter")
    @PreAuthorize("hasRole('PASSAGER')")
    public ResponseEntity<Notation> noterChauffeur(@PathVariable Long id,
                                                   @Valid @RequestBody NotationDTO dto,
                                                   @RequestHeader("Authorization") String token) {
        Notation notation = courseService.noterChauffeur(id, dto, passagerId(token));
        Double nouvelleMoyenne = courseService.calculerNoteMoyenneChauffeur(notation.getChauffeurId());
        authServiceClient.mettreAJourStatsChauffeur(notation.getChauffeurId(), nouvelleMoyenne);
        return ResponseEntity.ok(notation);
    }

    @PostMapping("/{id}/paiement")
    @PreAuthorize("hasRole('PASSAGER')")
    public ResponseEntity<Paiement> declarerPaiement(@PathVariable Long id,
                                                     @Valid @RequestBody DeclarerPaiementDTO dto,
                                                     @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(courseService.declarerPaiement(id, dto, passagerId(token)));
    }

    // Itinéraire réel + prix estimés, affichés AVANT la réservation.
    // Le prix final est recalculé côté serveur à la création (même calcul).
    @GetMapping("/itineraire")
    @PreAuthorize("hasRole('PASSAGER')")
    public ResponseEntity<Map<String, Object>> itineraire(@RequestParam double departLat, @RequestParam double departLng,
                                                          @RequestParam double arriveeLat, @RequestParam double arriveeLng) {
        ItineraireService.Itineraire it = itineraireService.calculer(departLat, departLng, arriveeLat, arriveeLng);
        Map<String, Object> reponse = new HashMap<>();
        reponse.put("distanceKm", Math.round(it.distanceKm() * 100) / 100.0);
        reponse.put("dureeMin", it.dureeMin());
        reponse.put("trace", it.trace());
        reponse.put("source", it.source());
        reponse.put("prixVoiture", courseService.calculerPrix("VOITURE", it.distanceKm()));
        reponse.put("prixMoto", courseService.calculerPrix("MOTO", it.distanceKm()));
        return ResponseEntity.ok(reponse);
    }

    // Temps restant avant l'annulation automatique (calculé par le serveur :
    // pas de souci de fuseau horaire entre le serveur et le téléphone)
    @GetMapping("/{id}/recherche")
    @PreAuthorize("hasRole('PASSAGER')")
    public ResponseEntity<Map<String, Object>> recherche(@PathVariable Long id,
                                                         @RequestHeader("Authorization") String token) {
        Course course = courseService.getCourseById(id);
        verifierAccesCourse(course, token);
        return ResponseEntity.ok(Map.of("statut", course.getStatut(),
                "secondesRestantes", courseService.secondesAvantAnnulationAuto(course)));
    }

    // Tracé routier départ → arrivée d'une course existante (mêmes droits que
    // la course). Déjà en cache depuis la réservation : pas de quota consommé.
    @GetMapping("/{id}/itineraire")
    @PreAuthorize("hasAnyRole('PASSAGER','CHAUFFEUR','ADMIN')")
    public ResponseEntity<Map<String, Object>> itineraireCourse(@PathVariable Long id,
                                                                @RequestHeader("Authorization") String token) {
        Course course = courseService.getCourseById(id);
        verifierAccesCourse(course, token);
        return ResponseEntity.ok(versReponse(itineraireDe(course)));
    }

    private ItineraireService.Itineraire itineraireDe(Course c) {
        return itineraireService.calculer(c.getDepartLat().doubleValue(), c.getDepartLng().doubleValue(),
                c.getArriveeLat().doubleValue(), c.getArriveeLng().doubleValue());
    }

    private static Map<String, Object> versReponse(ItineraireService.Itineraire it) {
        Map<String, Object> m = new HashMap<>();
        m.put("distanceKm", Math.round(it.distanceKm() * 100) / 100.0);
        m.put("dureeMin", it.dureeMin());
        m.put("trace", it.trace());
        m.put("source", it.source());
        return m;
    }

    // Le point de départ est-il dans une zone desservie ? (vérifié avant réservation)
    @GetMapping("/zone")
    @PreAuthorize("hasRole('PASSAGER')")
    public ResponseEntity<Map<String, Object>> zone(@RequestParam double lat, @RequestParam double lng) {
        Map<String, Object> reponse = new HashMap<>();
        try {
            reponse.put("villeId", courseService.determinerVilleDesservie(lat, lng));
            reponse.put("desservie", true);
        } catch (RuntimeException e) {
            reponse.put("desservie", false);
            reponse.put("message", e.getMessage());
        }
        return ResponseEntity.ok(reponse);
    }

    @GetMapping("/chauffeurs-disponibles")
    @PreAuthorize("hasRole('PASSAGER')")
    public ResponseEntity<List<Map<String, Object>>> chauffeursDisponibles(
            @RequestParam Long villeId, @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(authServiceClient.getChauffeursDisponibles(villeId, token));
    }

    // ── CHAUFFEUR ─────────────────────────────────────────

    @PutMapping("/{id}/accepter")
    @PreAuthorize("hasRole('CHAUFFEUR')")
    public ResponseEntity<Course> accepterCourse(@PathVariable Long id,
                                                 @RequestHeader("Authorization") String token) {
        Long chauffeurId = chauffeurId(token);
        BigDecimal seuil = parametreService.getValeurDecimal("SOLDE_MINIMUM_CHAUFFEUR", "500");
        if (!paiementServiceClient.soldeSuffisant(chauffeurId, seuil)) {
            throw new RuntimeException("Solde insuffisant (minimum " + seuil.toPlainString()
                    + " FCFA). Rechargez votre portefeuille pour accepter des courses.");
        }
        String type = (String) authServiceClient.getResumeChauffeur(chauffeurId).get("typeVehicule");
        return ResponseEntity.ok(courseService.accepterCourse(id, chauffeurId, type));
    }

    @GetMapping("/en-attente")
    @PreAuthorize("hasRole('CHAUFFEUR')")
    public ResponseEntity<List<Course>> coursesEnAttente(@RequestParam(required = false) Long villeId,
                                                         @RequestHeader("Authorization") String token) {
        // Ville et type de véhicule viennent du profil du chauffeur, pas du client
        Map<String, Object> resume = authServiceClient.getResumeChauffeur(chauffeurId(token));
        Object villeChauffeur = resume.get("villeId");
        Long ville = villeChauffeur instanceof Number n ? n.longValue() : villeId;
        if (ville == null) return ResponseEntity.ok(List.of());
        return ResponseEntity.ok(courseService.getCoursesEnAttente(ville, (String) resume.get("typeVehicule")));
    }

    @PutMapping("/{id}/statut")
    @PreAuthorize("hasRole('CHAUFFEUR')")
    public ResponseEntity<Course> mettreAJourStatut(@PathVariable Long id,
                                                    @Valid @RequestBody StatutCourseDTO dto,
                                                    @RequestHeader("Authorization") String token) {
        Long chauffeurId = chauffeurId(token);
        Course course = courseService.mettreAJourStatut(id, dto.getStatut(), chauffeurId);
        if ("TERMINEE".equals(dto.getStatut())) {
            // Les stats ne doivent pas faire échouer la fin de course
            try {
                authServiceClient.incrementerCoursesChauffeur(chauffeurId);
                if (course.getDistanceKm() != null) {
                    authServiceClient.ajouterKilometres(chauffeurId, course.getDistanceKm());
                }
            } catch (Exception e) {
                log.warn("Mise à jour des stats chauffeur {} impossible : {}", chauffeurId, e.getMessage());
            }
        }
        return ResponseEntity.ok(course);
    }

    @PutMapping("/{id}/annuler-chauffeur")
    @PreAuthorize("hasRole('CHAUFFEUR')")
    public ResponseEntity<Course> annulerCourseChauffeur(@PathVariable Long id,
                                                         @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(courseService.annulerCourse(id, chauffeurId(token), "CHAUFFEUR"));
    }

    @GetMapping("/chauffeur/historique")
    @PreAuthorize("hasRole('CHAUFFEUR')")
    public ResponseEntity<List<Course>> historiqueChauffeur(@RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(courseService.getHistoriqueChauffeur(chauffeurId(token)));
    }

    @GetMapping("/chauffeur/stats")
    @PreAuthorize("hasRole('CHAUFFEUR')")
    public ResponseEntity<StatsDTO> statsChauffeur(@RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(courseService.getStatsChauffeur(chauffeurId(token)));
    }

    // Infos du passager pour le chauffeur :
    //  - course EN_ATTENTE (avant acceptation) → prénom seulement ;
    //  - course assignée à CE chauffeur → prénom, nom, téléphone.
    @GetMapping("/{id}/passager")
    @PreAuthorize("hasAnyRole('CHAUFFEUR','ADMIN')")
    public ResponseEntity<Map<String, Object>> passagerDeLaCourse(@PathVariable Long id,
                                                                  @RequestHeader("Authorization") String token) {
        Course course = courseService.getCourseById(id);
        verifierAccesCourse(course, token);
        Map<String, Object> complet = authServiceClient.getPassagerPublic(course.getPassagerId());
        boolean assigne = course.getChauffeurId() != null
                && !"EN_ATTENTE".equals(course.getStatut());
        if (assigne || "ADMIN".equals(jwtService.extraireRole(token.substring(7)))) {
            return ResponseEntity.ok(complet);
        }
        Map<String, Object> reduit = new HashMap<>();
        reduit.put("prenom", complet != null ? complet.get("prenom") : null);
        return ResponseEntity.ok(reduit);
    }

    @PutMapping("/{id}/paiement/confirmer")
    @PreAuthorize("hasRole('CHAUFFEUR')")
    public ResponseEntity<Paiement> confirmerPaiement(@PathVariable Long id,
                                                      @RequestBody(required = false) Map<String, String> body,
                                                      @RequestHeader("Authorization") String token) {
        Long chauffeurId = chauffeurId(token);
        String mode = body != null ? body.get("mode") : null;
        if (mode != null && !mode.matches("(?i)^(LIQUIDE|MTN|ORANGE)$")) {
            throw new RuntimeException("Mode : LIQUIDE, MTN ou ORANGE");
        }
        Paiement paiement = courseService.confirmerPaiement(id, chauffeurId, mode);
        paiementServiceClient.deduireCommission(chauffeurId, paiement.getMontant(), paiement.getCommission(), id);
        return ResponseEntity.ok(paiement);
    }

    // Position GPS du chauffeur pendant la course (envoyée toutes les ~10 s)
    @PutMapping("/{id}/position")
    @PreAuthorize("hasRole('CHAUFFEUR')")
    public ResponseEntity<Void> mettreAJourPosition(@PathVariable Long id,
                                                    @Valid @RequestBody PositionDTO dto,
                                                    @RequestHeader("Authorization") String token) {
        courseService.mettreAJourPosition(id, chauffeurId(token), dto);
        return ResponseEntity.noContent().build();
    }

    // Course en cours du chauffeur (reprise après rechargement de la page)
    @GetMapping("/chauffeur/active")
    @PreAuthorize("hasRole('CHAUFFEUR')")
    public ResponseEntity<Map<String, Object>> courseActiveChauffeur(@RequestHeader("Authorization") String token) {
        Long chauffeurId = chauffeurId(token);
        Map<String, Object> reponse = new HashMap<>();
        reponse.put("course", courseService.getCourseActiveChauffeur(chauffeurId).orElse(null));
        reponse.put("courseAPayer", courseService.getCourseAPayerChauffeur(chauffeurId).orElse(null));
        return ResponseEntity.ok(reponse);
    }

    // Course active du passager (reprise du suivi après rechargement de la page)
    @GetMapping("/active")
    @PreAuthorize("hasRole('PASSAGER')")
    public ResponseEntity<Course> courseActivePassager(@RequestHeader("Authorization") String token) {
        return courseService.getCourseActivePassager(passagerId(token))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    // Le passager génère un lien public de suivi à envoyer à un proche
    @PostMapping("/{id}/partage")
    @PreAuthorize("hasRole('PASSAGER')")
    public ResponseEntity<Map<String, String>> partagerTrajet(@PathVariable Long id,
                                                              @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(Map.of("token", courseService.genererLienPartage(id, passagerId(token))));
    }

    // PUBLIC (sans compte) : suivi d'un trajet partagé. Informations limitées :
    // pas de téléphone, pas d'identifiant, plus rien une fois la course finie.
    @GetMapping("/partage/{token}")
    public ResponseEntity<Map<String, Object>> suiviPartage(@PathVariable String token) {
        Course c = courseService.getCourseParPartage(token);
        Map<String, Object> vue = new HashMap<>();
        vue.put("statut", c.getStatut());
        if (!CourseService.STATUTS_ACTIFS.contains(c.getStatut())) {
            return ResponseEntity.ok(vue); // trajet terminé ou annulé : plus de position
        }
        vue.put("typeVehicule", c.getTypeVehicule());
        vue.put("departAdresse", c.getDepartAdresse());
        vue.put("departLat", c.getDepartLat());
        vue.put("departLng", c.getDepartLng());
        vue.put("arriveeAdresse", c.getArriveeAdresse());
        vue.put("arriveeLat", c.getArriveeLat());
        vue.put("arriveeLng", c.getArriveeLng());
        vue.put("chauffeurLat", c.getChauffeurLat());
        vue.put("chauffeurLng", c.getChauffeurLng());
        vue.put("positionMajAt", c.getPositionMajAt());
        try {
            vue.put("trace", itineraireDe(c).trace());
        } catch (Exception e) {
            log.warn("Tracé indisponible pour le partage : {}", e.getMessage());
        }
        vue.put("startedAt", c.getStartedAt());
        if (c.getChauffeurId() != null) {
            try {
                Map<String, Object> r = authServiceClient.getResumeChauffeur(c.getChauffeurId());
                vue.put("chauffeurPrenom", r.get("prenom"));
                vue.put("vehicule", (r.getOrDefault("marque", "") + " " + r.getOrDefault("modele", "")).trim());
                vue.put("vehiculeCouleur", r.get("couleur"));
                vue.put("immatriculation", r.get("immatriculation"));
            } catch (Exception e) {
                log.warn("Résumé chauffeur indisponible pour le partage : {}", e.getMessage());
            }
        }
        return ResponseEntity.ok(vue);
    }

    // ── COMMUN ────────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PASSAGER','CHAUFFEUR','ADMIN')")
    public ResponseEntity<Course> detailCourse(@PathVariable Long id,
                                               @RequestHeader("Authorization") String token) {
        Course course = courseService.getCourseById(id);
        verifierAccesCourse(course, token);
        return ResponseEntity.ok(course);
    }

    // 204 si aucun paiement n'est encore déclaré (n'est pas une erreur)
    @GetMapping("/{id}/paiement")
    @PreAuthorize("hasAnyRole('PASSAGER','CHAUFFEUR','ADMIN')")
    public ResponseEntity<Paiement> getPaiement(@PathVariable Long id,
                                                @RequestHeader("Authorization") String token) {
        verifierAccesCourse(courseService.getCourseById(id), token);
        try {
            return ResponseEntity.ok(courseService.getPaiementParCourse(id));
        } catch (RuntimeException e) {
            return ResponseEntity.noContent().build();
        }
    }

    @GetMapping("/rechercher-adresse")
    @PreAuthorize("hasAnyRole('PASSAGER','CHAUFFEUR')")
    public ResponseEntity<List<SuggestionAdresseDTO>> rechercherAdresse(@RequestParam String q) {
        if (q == null || q.trim().length() < 2 || q.length() > 120) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(geocodageService.rechercherAdresses(q));
    }

    // Tarifs (lecture seule) pour l'estimation avant réservation ;
    // le prix final reste calculé côté serveur.
    @GetMapping("/tarifs")
    @PreAuthorize("hasAnyRole('PASSAGER','CHAUFFEUR')")
    public ResponseEntity<Map<String, BigDecimal>> getTarifs() {
        return ResponseEntity.ok(Map.of(
                "tarifVoitureKm", parametreService.getValeurDecimal("TARIF_VOITURE_KM", "500"),
                "tarifMotoKm", parametreService.getValeurDecimal("TARIF_MOTO_KM", "300"),
                "prixMinimum", parametreService.getValeurDecimal("PRIX_MINIMUM", "500"),
                "commissionPourcentage", parametreService.getValeurDecimal("COMMISSION_POURCENTAGE", "15"),
                "soldeMinimum", parametreService.getValeurDecimal("SOLDE_MINIMUM_CHAUFFEUR", "500"),
                "timerCourse", parametreService.getValeurDecimal("TIMER_COURSE", "15"),
                "distanceMaxKm", parametreService.getValeurDecimal("DISTANCE_MAX_KM", "60")
        ));
    }

    // ── ADMIN ─────────────────────────────────────────────

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Course>> toutesCourses() {
        return ResponseEntity.ok(courseService.getToutesCourses());
    }

    @GetMapping("/admin/en-cours")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Course>> coursesEnCours() {
        return ResponseEntity.ok(courseService.getCoursesEnCours());
    }

    @PutMapping("/admin/{id}/annuler")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Course> annulerCourseAdmin(@PathVariable Long id,
                                                     @RequestHeader("Authorization") String token) {
        Course course = courseService.annulerCourse(id, null, "ADMIN");
        authServiceClient.journaliser(jwtService.extraireEmail(token.substring(7)),
                "Annulation de la course #" + id, "COURSE", id);
        return ResponseEntity.ok(course);
    }

    // Paiements de toutes les courses (commissions, modes) pour l'admin
    @GetMapping("/admin/paiements")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Paiement>> paiementsAdmin() {
        return ResponseEntity.ok(courseService.getTousPaiements());
    }
}

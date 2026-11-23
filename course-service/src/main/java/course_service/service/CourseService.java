package course_service.service;

import course_service.dto.CreerCourseDTO;
import course_service.dto.DeclarerPaiementDTO;
import course_service.dto.NotationDTO;
import course_service.dto.PositionDTO;
import course_service.dto.StatsDTO;
import course_service.entity.Course;
import course_service.entity.Notation;
import course_service.entity.Paiement;
import course_service.repository.CourseRepository;
import course_service.repository.NotationRepository;
import course_service.repository.PaiementRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CourseService {

    private static final Logger log = LoggerFactory.getLogger(CourseService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    public static final List<String> STATUTS_ACTIFS =
            List.of("EN_ATTENTE", "ACCEPTEE", "EN_ROUTE", "ARRIVEE", "DEMARREE");
    // Statuts où un chauffeur est assigné et la course pas encore terminée
    public static final List<String> STATUTS_EN_COURS =
            List.of("ACCEPTEE", "EN_ROUTE", "ARRIVEE", "DEMARREE");

    private final CourseRepository courseRepository;
    private final NotationRepository notationRepository;
    private final ParametreService parametreService;
    private final PaiementRepository paiementRepository;
    private final course_service.client.AuthServiceClient authServiceClient;
    private final ItineraireService itineraireService;

    // ── CREER UNE COURSE ──────────────────────────────────
    @Transactional
    public Course creerCourse(CreerCourseDTO dto, Long passagerId) {
        if (courseRepository.existsByPassagerIdAndStatutIn(passagerId, STATUTS_ACTIFS)) {
            throw new RuntimeException("Vous avez déjà une course en cours. Terminez-la ou annulez-la d'abord.");
        }

        // Distance RÉELLE par la route (OpenRouteService), sinon estimation vol d'oiseau × 1,4
        double distance = itineraireService.calculer(
                dto.getDepartLat().doubleValue(), dto.getDepartLng().doubleValue(),
                dto.getArriveeLat().doubleValue(), dto.getArriveeLng().doubleValue()).distanceKm();

        if (distance < 0.2) {
            throw new RuntimeException("La destination est trop proche de votre position");
        }
        BigDecimal distanceMax = parametreService.getValeurDecimal("DISTANCE_MAX_KM", "60");
        if (BigDecimal.valueOf(distance).compareTo(distanceMax) > 0) {
            throw new RuntimeException("Trajet trop long (maximum " + distanceMax.toPlainString() + " km)");
        }

        // La ville de la course dépend du POINT DE DÉPART, pas de la ville du profil :
        // un étudiant inscrit à Yaoundé mais présent ailleurs n'appelle pas les chauffeurs de Yaoundé.
        Long villeId = determinerVilleDesservie(dto.getDepartLat().doubleValue(), dto.getDepartLng().doubleValue());

        String type = dto.getTypeVehicule().toUpperCase();

        // Personne ne pourrait accepter cette course : on refuse tout de suite
        // plutôt que de laisser le passager attendre l'annulation automatique.
        long disponibles;
        try {
            disponibles = authServiceClient.compterChauffeursEnLigne(villeId, type);
        } catch (Exception e) {
            // auth-service momentanément injoignable : on n'empêche pas la réservation
            log.warn("Comptage des chauffeurs en ligne impossible : {}", e.getMessage());
            disponibles = 1;
        }
        if (disponibles == 0) {
            throw new RuntimeException("Aucun chauffeur " + ("MOTO".equals(type) ? "moto" : "voiture")
                    + " n'est en ligne dans votre zone pour le moment. Réessayez dans quelques minutes"
                    + ("MOTO".equals(type) ? " ou choisissez une voiture." : " ou choisissez une moto."));
        }

        Course course = new Course();
        course.setPassagerId(passagerId);
        course.setVilleId(villeId);
        course.setTypeVehicule(type);
        course.setDepartAdresse(dto.getDepartAdresse().trim());
        course.setDepartLat(dto.getDepartLat());
        course.setDepartLng(dto.getDepartLng());
        course.setArriveeAdresse(dto.getArriveeAdresse().trim());
        course.setArriveeLat(dto.getArriveeLat());
        course.setArriveeLng(dto.getArriveeLng());
        course.setDistanceKm(BigDecimal.valueOf(distance).setScale(2, RoundingMode.HALF_UP));
        course.setPrixEstime(calculerPrix(type, distance));
        course.setStatut("EN_ATTENTE");
        return courseRepository.save(course);
    }

    /**
     * Ville desservie la plus proche du point donné, dans le rayon défini par
     * l'admin (RAYON_DESSERTE_KM). Hors zone → réservation refusée.
     */
    public Long determinerVilleDesservie(double lat, double lng) {
        double rayon = parametreService.getValeurDecimal("RAYON_DESSERTE_KM", "30").doubleValue();
        Long meilleure = null;
        double distanceMin = Double.MAX_VALUE;
        for (var ville : authServiceClient.getVillesActives()) {
            if (!(ville.get("lat") instanceof Number vLat) || !(ville.get("lng") instanceof Number vLng)) continue;
            double d = calculerDistanceKm(lat, lng, vLat.doubleValue(), vLng.doubleValue());
            if (d <= rayon && d < distanceMin) {
                distanceMin = d;
                meilleure = ((Number) ville.get("id")).longValue();
            }
        }
        if (meilleure == null) {
            throw new RuntimeException("School Driva n'est pas encore disponible dans votre zone. "
                    + "Vérifiez votre point de départ.");
        }
        return meilleure;
    }

    // ── ACCEPTER UNE COURSE ───────────────────────────────
    @Transactional
    public Course accepterCourse(Long courseId, Long chauffeurId, String typeVehiculeChauffeur) {
        Course course = getCourseById(courseId);

        if (!"EN_ATTENTE".equals(course.getStatut())) {
            throw new RuntimeException("Cette course n'est plus disponible");
        }
        if (typeVehiculeChauffeur != null && !typeVehiculeChauffeur.equalsIgnoreCase(course.getTypeVehicule())) {
            throw new RuntimeException("Cette course demande un véhicule de type " + course.getTypeVehicule());
        }
        if (!courseRepository.findByChauffeurIdAndStatutIn(chauffeurId, STATUTS_EN_COURS).isEmpty()) {
            throw new RuntimeException("Vous avez déjà une course en cours. Terminez-la avant d'en accepter une nouvelle.");
        }
        for (Course c : courseRepository.findByChauffeurIdAndStatutIn(chauffeurId, List.of("TERMINEE"))) {
            Optional<Paiement> p = paiementRepository.findByCourseId(c.getId());
            if (p.isEmpty() || "EN_ATTENTE".equals(p.get().getStatut())) {
                throw new RuntimeException("Confirmez d'abord le paiement de votre course précédente (#" + c.getId() + ").");
            }
        }

        if (courseRepository.accepterSiDisponible(courseId, chauffeurId) == 0) {
            throw new RuntimeException("Cette course vient d'être acceptée par un autre chauffeur");
        }
        return getCourseById(courseId);
    }

    // ── METTRE A JOUR LE STATUT ───────────────────────────
    @Transactional
    public Course mettreAJourStatut(Long courseId, String nouveauStatut, Long chauffeurId) {
        Course course = getCourseById(courseId);
        verifierChauffeur(course, chauffeurId);
        validerTransitionStatut(course.getStatut(), nouveauStatut);

        course.setStatut(nouveauStatut);
        if ("DEMARREE".equals(nouveauStatut)) {
            course.setStartedAt(LocalDateTime.now());
        }
        if ("TERMINEE".equals(nouveauStatut)) {
            course.setEndedAt(LocalDateTime.now());
            if (course.getStartedAt() != null) {
                course.setDureeMin((int) Duration.between(course.getStartedAt(), course.getEndedAt()).toMinutes());
            }
            // MVP : prix final = prix estimé (calculé côté serveur à la création)
            course.setPrixFinal(course.getPrixEstime());
        }
        return courseRepository.save(course);
    }

    // ── ANNULER UNE COURSE ────────────────────────────────
    //  - passager : tant que la course n'a pas démarré ;
    //  - chauffeur : la course est REMISE EN RECHERCHE (un autre chauffeur peut la prendre) ;
    //  - admin : à tout moment avant la fin.
    @Transactional
    public Course annulerCourse(Long courseId, Long userId, String annuleePar) {
        Course course = getCourseById(courseId);

        switch (annuleePar) {
            case "PASSAGER" -> {
                if (!course.getPassagerId().equals(userId)) {
                    throw new AccessDeniedException("Cette course ne vous appartient pas");
                }
                if (!List.of("EN_ATTENTE", "ACCEPTEE", "EN_ROUTE", "ARRIVEE").contains(course.getStatut())) {
                    throw new RuntimeException("Cette course ne peut plus être annulée");
                }
            }
            case "CHAUFFEUR" -> {
                verifierChauffeur(course, userId);
                if (!List.of("ACCEPTEE", "EN_ROUTE", "ARRIVEE").contains(course.getStatut())) {
                    throw new RuntimeException("Cette course ne peut plus être annulée");
                }
                course.setChauffeurId(null);
                course.setStatut("EN_ATTENTE");
                course.setChauffeurLat(null);
                course.setChauffeurLng(null);
                course.setPositionMajAt(null);
                return courseRepository.save(course);
            }
            case "ADMIN" -> {
                if (!STATUTS_ACTIFS.contains(course.getStatut())) {
                    throw new RuntimeException("Cette course est déjà terminée ou annulée");
                }
            }
            default -> throw new AccessDeniedException("Annulation non autorisée");
        }

        course.setStatut("ANNULEE");
        course.setAnnuleePar(annuleePar);
        course.setPrixFinal(null);
        return courseRepository.save(course);
    }

    /** Secondes restantes avant l'annulation automatique d'une course sans chauffeur. */
    public long secondesAvantAnnulationAuto(Course course) {
        if (!"EN_ATTENTE".equals(course.getStatut()) || course.getCreatedAt() == null) return 0;
        int minutes = parametreService.getValeurDecimal("DELAI_RECHERCHE_MIN", "10").intValue();
        long restantes = Duration.between(LocalDateTime.now(), course.getCreatedAt().plusMinutes(minutes)).getSeconds();
        return Math.max(0, restantes);
    }

    // Courses restées sans chauffeur trop longtemps → annulées automatiquement
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    @Transactional
    public void expirerCoursesSansChauffeur() {
        int minutes = parametreService.getValeurDecimal("DELAI_RECHERCHE_MIN", "10").intValue();
        List<Course> expirees = courseRepository.findByStatutAndCreatedAtBefore(
                "EN_ATTENTE", LocalDateTime.now().minusMinutes(minutes));
        for (Course c : expirees) {
            c.setStatut("ANNULEE");
            c.setAnnuleePar("SYSTEME");
            c.setPrixFinal(null);
        }
        if (!expirees.isEmpty()) {
            courseRepository.saveAll(expirees);
            log.info("{} course(s) sans chauffeur annulée(s) automatiquement", expirees.size());
        }
    }

    // ── POSITION DU CHAUFFEUR (suivi en temps réel) ───────
    @Transactional
    public void mettreAJourPosition(Long courseId, Long chauffeurId, PositionDTO dto) {
        Course course = getCourseById(courseId);
        verifierChauffeur(course, chauffeurId);
        if (!STATUTS_EN_COURS.contains(course.getStatut())) {
            throw new RuntimeException("Cette course n'est plus en cours");
        }
        course.setChauffeurLat(dto.getLat());
        course.setChauffeurLng(dto.getLng());
        course.setPositionMajAt(LocalDateTime.now());
        courseRepository.save(course);
    }

    // ── PARTAGE DU TRAJET ─────────────────────────────────
    @Transactional
    public String genererLienPartage(Long courseId, Long passagerId) {
        Course course = getCourseById(courseId);
        if (!course.getPassagerId().equals(passagerId)) {
            throw new AccessDeniedException("Cette course ne vous appartient pas");
        }
        if (!STATUTS_ACTIFS.contains(course.getStatut())) {
            throw new RuntimeException("Seul un trajet en cours peut être partagé");
        }
        if (course.getPartageToken() == null) {
            byte[] octets = new byte[24];
            RANDOM.nextBytes(octets);
            course.setPartageToken(Base64.getUrlEncoder().withoutPadding().encodeToString(octets));
            courseRepository.save(course);
        }
        return course.getPartageToken();
    }

    public Course getCourseParPartage(String token) {
        if (token == null || token.length() < 20) {
            throw new RuntimeException("Lien de suivi invalide");
        }
        return courseRepository.findByPartageToken(token)
                .orElseThrow(() -> new RuntimeException("Lien de suivi invalide ou expiré"));
    }

    // ── COURSE ACTIVE ─────────────────────────────────────
    public Optional<Course> getCourseActivePassager(Long passagerId) {
        return courseRepository.findByPassagerIdAndStatutIn(passagerId, STATUTS_ACTIFS).stream().findFirst();
    }

    public Optional<Course> getCourseActiveChauffeur(Long chauffeurId) {
        return courseRepository.findByChauffeurIdAndStatutIn(chauffeurId, STATUTS_EN_COURS).stream().findFirst();
    }

    // Dernière course terminée du chauffeur dont le paiement n'est pas confirmé
    public Optional<Course> getCourseAPayerChauffeur(Long chauffeurId) {
        return courseRepository.findByChauffeurIdAndStatutIn(chauffeurId, List.of("TERMINEE")).stream()
                .filter(c -> paiementRepository.findByCourseId(c.getId())
                        .map(p -> "EN_ATTENTE".equals(p.getStatut())).orElse(true))
                .findFirst();
    }

    // ── NOTER UN CHAUFFEUR ────────────────────────────────
    @Transactional
    public Notation noterChauffeur(Long courseId, NotationDTO dto, Long passagerId) {
        Course course = getCourseById(courseId);
        if (!course.getPassagerId().equals(passagerId)) {
            throw new AccessDeniedException("Vous n'êtes pas le passager de cette course");
        }
        if (!"TERMINEE".equals(course.getStatut())) {
            throw new RuntimeException("La course doit être terminée pour être notée");
        }
        if (notationRepository.existsByCourseId(courseId)) {
            throw new RuntimeException("Cette course a déjà été notée");
        }
        Notation notation = new Notation();
        notation.setCourseId(courseId);
        notation.setPassagerId(passagerId);
        notation.setChauffeurId(course.getChauffeurId());
        notation.setNote(dto.getNote());
        notation.setCommentaire(dto.getCommentaire());
        notation.setSecuriteOk(dto.getSecuriteOk());
        notation.setTags(dto.getTags());
        return notationRepository.save(notation);
    }

    public Double calculerNoteMoyenneChauffeur(Long chauffeurId) {
        return notationRepository.calculerNoteMoyenne(chauffeurId);
    }

    public Optional<Notation> getNotation(Long courseId) {
        return notationRepository.findByCourseId(courseId);
    }

    // ── LECTURES ──────────────────────────────────────────
    public List<Course> getHistoriquePassager(Long passagerId) {
        return courseRepository.findByPassagerIdOrderByCreatedAtDesc(passagerId);
    }

    public List<Course> getHistoriqueChauffeur(Long chauffeurId) {
        return courseRepository.findByChauffeurIdOrderByCreatedAtDesc(chauffeurId);
    }

    public StatsDTO getStatsChauffeur(Long chauffeurId) {
        Long terminees = courseRepository.countByChauffeurIdAndStatut(chauffeurId, "TERMINEE");
        Long annulees = courseRepository.countByChauffeurIdAndStatut(chauffeurId, "ANNULEE");
        return new StatsDTO(terminees + annulees, terminees, annulees,
                notationRepository.calculerNoteMoyenne(chauffeurId));
    }

    public List<Course> getToutesCourses() {
        return courseRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<Course> getCoursesEnCours() {
        return courseRepository.findByStatutIn(STATUTS_ACTIFS);
    }

    public List<Paiement> getTousPaiements() {
        return paiementRepository.findAll();
    }

    public List<Paiement> getPaiementsDesCourses(List<Long> courseIds) {
        return courseIds.isEmpty() ? List.of() : paiementRepository.findByCourseIdIn(courseIds);
    }

    public Course getCourseById(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Course introuvable"));
    }

    public List<Course> getCoursesEnAttente(Long villeId, String typeVehicule) {
        return typeVehicule == null
                ? courseRepository.findByStatutAndVilleIdOrderByCreatedAtAsc("EN_ATTENTE", villeId)
                : courseRepository.findByStatutAndVilleIdAndTypeVehiculeOrderByCreatedAtAsc("EN_ATTENTE", villeId, typeVehicule);
    }

    // ── PAIEMENT DE LA COURSE ─────────────────────────────

    /** Commission selon le taux défini par l'admin (paramètre COMMISSION_POURCENTAGE). */
    public BigDecimal calculerCommission(BigDecimal montant) {
        BigDecimal taux = parametreService.getValeurDecimal("COMMISSION_POURCENTAGE", "15");
        return montant.multiply(taux).divide(new BigDecimal("100"), 0, RoundingMode.HALF_UP);
    }

    private Paiement nouveauPaiement(Course course, String mode) {
        BigDecimal montant = course.getPrixFinal() != null ? course.getPrixFinal() : course.getPrixEstime();
        BigDecimal commission = calculerCommission(montant);
        Paiement paiement = new Paiement();
        paiement.setCourseId(course.getId());
        paiement.setMode(mode.toUpperCase());
        paiement.setMontant(montant);
        paiement.setCommission(commission);
        paiement.setGainChauffeur(montant.subtract(commission));
        paiement.setStatut("EN_ATTENTE");
        return paiement;
    }

    @Transactional
    public Paiement declarerPaiement(Long courseId, DeclarerPaiementDTO dto, Long passagerId) {
        Course course = getCourseById(courseId);
        if (!course.getPassagerId().equals(passagerId)) {
            throw new AccessDeniedException("Vous n'êtes pas le passager de cette course");
        }
        if (!"TERMINEE".equals(course.getStatut())) {
            throw new RuntimeException("La course doit être terminée pour déclarer un paiement");
        }
        Optional<Paiement> existant = paiementRepository.findByCourseId(courseId);
        if (existant.isPresent()) {
            // Le chauffeur a peut-être déjà confirmé : on renvoie l'état actuel
            return existant.get();
        }
        return paiementRepository.save(nouveauPaiement(course, dto.getMode()));
    }

    public Paiement getPaiementParCourse(Long courseId) {
        return paiementRepository.findByCourseId(courseId)
                .orElseThrow(() -> new RuntimeException("Aucun paiement déclaré pour cette course"));
    }

    /**
     * Le chauffeur confirme avoir reçu le paiement. Si le passager n'a rien
     * déclaré (application fermée, pas de réseau...), le paiement est créé ici :
     * le chauffeur n'est plus bloqué indéfiniment.
     */
    @Transactional
    public Paiement confirmerPaiement(Long courseId, Long chauffeurId, String mode) {
        Course course = getCourseById(courseId);
        verifierChauffeur(course, chauffeurId);
        if (!"TERMINEE".equals(course.getStatut())) {
            throw new RuntimeException("La course n'est pas terminée");
        }
        Paiement paiement = paiementRepository.findByCourseId(courseId)
                .orElseGet(() -> nouveauPaiement(course, mode == null ? "LIQUIDE" : mode));
        if ("CONFIRME".equals(paiement.getStatut())) {
            throw new RuntimeException("Ce paiement a déjà été confirmé");
        }
        if (mode != null && !mode.isBlank()) {
            paiement.setMode(mode.toUpperCase());
        }
        paiement.setStatut("CONFIRME");
        paiement.setConfirmeAt(LocalDateTime.now());
        return paiementRepository.save(paiement);
    }

    // ── OUTILS ────────────────────────────────────────────
    private void verifierChauffeur(Course course, Long chauffeurId) {
        if (course.getChauffeurId() == null || !course.getChauffeurId().equals(chauffeurId)) {
            throw new AccessDeniedException("Vous n'êtes pas le chauffeur de cette course");
        }
    }

    public BigDecimal calculerPrix(String typeVehicule, double distanceKm) {
        BigDecimal prixMinimum = parametreService.getValeurDecimal("PRIX_MINIMUM", "500");
        BigDecimal tarif = "MOTO".equalsIgnoreCase(typeVehicule)
                ? parametreService.getValeurDecimal("TARIF_MOTO_KM", "300")
                : parametreService.getValeurDecimal("TARIF_VOITURE_KM", "500");
        BigDecimal prix = tarif.multiply(BigDecimal.valueOf(distanceKm));
        // Arrondi aux 50 FCFA supérieurs (monnaie pratique)
        BigDecimal arrondi = prix.divide(new BigDecimal("50"), 0, RoundingMode.CEILING).multiply(new BigDecimal("50"));
        return arrondi.max(prixMinimum).setScale(0, RoundingMode.HALF_UP);
    }

    // Formule de Haversine (distance à vol d'oiseau)
    public static double calculerDistanceKm(double lat1, double lng1, double lat2, double lng2) {
        final int RAYON_TERRE = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return RAYON_TERRE * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private void validerTransitionStatut(String actuel, String nouveau) {
        boolean valide = switch (actuel) {
            case "ACCEPTEE" -> "EN_ROUTE".equals(nouveau);
            case "EN_ROUTE" -> "ARRIVEE".equals(nouveau);
            case "ARRIVEE" -> "DEMARREE".equals(nouveau);
            case "DEMARREE" -> "TERMINEE".equals(nouveau);
            default -> false;
        };
        if (!valide) {
            throw new RuntimeException("Transition de statut invalide : " + actuel + " → " + nouveau);
        }
    }
}

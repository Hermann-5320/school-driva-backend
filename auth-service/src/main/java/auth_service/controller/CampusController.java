package auth_service.controller;

import auth_service.entity.PointCampus;
import auth_service.repository.PointCampusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Points campus pré-enregistrés, proposés comme destinations rapides. */
@RestController
@RequestMapping("/api/campus")
@RequiredArgsConstructor
public class CampusController {

    private final PointCampusRepository pointCampusRepository;

    @GetMapping
    public ResponseEntity<List<PointCampus>> liste(@RequestParam(required = false) Long villeId) {
        return ResponseEntity.ok(villeId != null
                ? pointCampusRepository.findByVilleIdAndActifTrueOrderByNomAsc(villeId)
                : pointCampusRepository.findByActifTrueOrderByNomAsc());
    }
}

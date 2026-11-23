package auth_service.controller;

import auth_service.entity.Ville;
import auth_service.repository.VilleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/villes")
@RequiredArgsConstructor
public class VilleController {

    private final VilleRepository villeRepository;

    // GET /api/villes
    @GetMapping
    public ResponseEntity<List<Ville>> getVillesActives() {
        return ResponseEntity.ok(villeRepository.findByActiveTrue());
    }
}
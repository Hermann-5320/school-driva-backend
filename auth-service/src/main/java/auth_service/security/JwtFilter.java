package auth_service.security;

import auth_service.entity.Utilisateur;
import auth_service.repository.UtilisateurRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UtilisateurRepository utilisateurRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            if (jwtService.estTokenAcces(token)) {
                String email = jwtService.extraireEmail(token);
                if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    var utilisateur = utilisateurRepository.findByEmail(email);
                    // Un compte bloqué perd l'accès immédiatement, même avec un token encore valide.
                    // Le rôle vient de la base (source de vérité), pas du token.
                    if (utilisateur.isPresent()
                            && utilisateur.get().getStatut() != Utilisateur.Statut.BLOQUE
                            && jwtService.estValide(token, email)) {
                        var auth = new UsernamePasswordAuthenticationToken(
                                email, null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + utilisateur.get().getRole().name())));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                }
            }
        } catch (Exception e) {
            // Token invalide ou expiré : la requête continue sans authentification
        }

        filterChain.doFilter(request, response);
    }
}

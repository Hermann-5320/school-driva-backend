package auth_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TokenDTO {
    private String token;
    private String role;
    private Long userId;
    // true → le frontend impose l'écran de changement de mot de passe
    private boolean doitChangerMotDePasse;
}

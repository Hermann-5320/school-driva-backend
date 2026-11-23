package auth_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Envoi d'emails, par ordre de priorité :
 *  1. API HTTP Brevo si BREVO_API_KEY est défini (recommandé sur Render :
 *     l'offre gratuite bloque le SMTP sortant) ;
 *  2. SMTP (Gmail) si SPRING_MAIL_USERNAME est défini ;
 *  3. sinon : le message est seulement écrit dans les logs (démo locale).
 * Un email qui échoue ne fait JAMAIS échouer l'opération métier.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final RestTemplate restTemplate;

    @Value("${app.mail.brevo-api-key:}")
    private String brevoApiKey;

    @Value("${app.mail.expediteur:no-reply@schooldriva.cm}")
    private String expediteur;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    public EmailService(ObjectProvider<JavaMailSender> mailSender) {
        this.mailSender = mailSender;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(5000);
        f.setReadTimeout(10000);
        this.restTemplate = new RestTemplate(f);
    }

    /** @return true si l'email a réellement été transmis à un fournisseur. */
    public boolean envoyer(String destinataire, String sujet, String texte) {
        try {
            if (brevoApiKey != null && !brevoApiKey.isBlank()) {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("api-key", brevoApiKey);
                Map<String, Object> body = Map.of(
                        "sender", Map.of("email", expediteur, "name", "School Driva"),
                        "to", List.of(Map.of("email", destinataire)),
                        "subject", sujet,
                        "textContent", texte
                );
                restTemplate.postForEntity("https://api.brevo.com/v3/smtp/email",
                        new HttpEntity<>(body, headers), String.class);
                return true;
            }
            JavaMailSender sender = mailSender.getIfAvailable();
            if (sender != null && smtpUsername != null && !smtpUsername.isBlank()) {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(smtpUsername);
                message.setTo(destinataire);
                message.setSubject(sujet);
                message.setText(texte);
                sender.send(message);
                return true;
            }
            log.warn("[EMAIL NON CONFIGURÉ] À : {} | Sujet : {}\n{}", destinataire, sujet, texte);
            return false;
        } catch (Exception e) {
            log.error("Échec d'envoi d'email à {} : {}", destinataire, e.getMessage());
            return false;
        }
    }

    public boolean envoyerConfirmationInscription(String email, String prenom) {
        return envoyer(email, "Bienvenue sur School Driva !",
                "Bonjour " + prenom + ",\n\n" +
                "Votre compte School Driva a été créé avec succès.\n" +
                "Vous pouvez maintenant réserver des courses sur notre plateforme.\n\n" +
                "Bonne route !\nL'équipe School Driva");
    }

    public boolean envoyerConfirmationDossier(String email, String prenom, String lienDossier) {
        return envoyer(email, "School Driva : dossier reçu",
                "Bonjour " + prenom + ",\n\n" +
                "Nous avons bien reçu votre candidature.\n" +
                "Notre équipe va examiner votre dossier dans les 48 heures.\n\n" +
                "Il vous manque un document, ou vous devez en renvoyer un ? Utilisez votre lien personnel\n" +
                "(valable 48 heures, vous pourrez en redemander un depuis la page de connexion) :\n\n" +
                lienDossier + "\n\n" +
                "Vous recevrez un email dès que votre compte sera activé.\n\n" +
                "Merci de votre confiance.\nL'équipe School Driva");
    }

    public boolean envoyerCodeReset(String email, String code) {
        return envoyer(email, "Réinitialisation de mot de passe School Driva",
                "Bonjour,\n\nVotre code de réinitialisation est :\n\n   " + code + "\n\n" +
                "Ce code est valable 15 minutes.\n" +
                "Si vous n'avez pas demandé cette réinitialisation, ignorez cet email.\n\n" +
                "L'équipe School Driva");
    }

    public boolean envoyerActivationCompte(String email, String prenom, String motDePasse) {
        return envoyer(email, "Compte activé — School Driva",
                "Bonjour " + prenom + ",\n\n" +
                "Votre dossier a été validé. Votre compte est maintenant actif.\n\n" +
                "Email : " + email + "\nMot de passe temporaire : " + motDePasse + "\n\n" +
                "Vous devrez choisir un nouveau mot de passe à la première connexion.\n\n" +
                "Bienvenue dans l'équipe School Driva !");
    }

    public boolean envoyerIdentifiantsAdmin(String email, String nom, String motDePasse) {
        return envoyer(email, "Votre accès administrateur School Driva",
                "Bonjour " + nom + ",\n\nUn compte administrateur vient d'être créé pour vous.\n\n" +
                "Email : " + email + "\nMot de passe temporaire : " + motDePasse + "\n\n" +
                "Vous devrez le changer à la première connexion.");
    }

    public boolean envoyerLienDossier(String email, String prenom, String lien) {
        return envoyer(email, "School Driva : complétez votre dossier chauffeur",
                "Bonjour " + prenom + ",\n\n" +
                "Voici votre lien personnel pour compléter votre dossier chauffeur :\n\n" +
                lien + "\n\n" +
                "Ce lien est valable 48 heures. Il vous permet uniquement d'envoyer vos documents.\n" +
                "Si vous n'êtes pas à l'origine de cette demande, ignorez cet email.\n\n" +
                "L'équipe School Driva");
    }

    public boolean envoyerDocumentRejete(String email, String prenom, String typeDocument, String raison, String lien) {
        String suite = lien != null
                ? "Renvoyez ce document via votre lien personnel (valable 48 heures) :\n\n" + lien + "\n\n"
                : "Connectez-vous à votre espace chauffeur, rubrique « Documents », pour le renvoyer.\n\n";
        return envoyer(email, "School Driva : un document est à renvoyer",
                "Bonjour " + prenom + ",\n\n" +
                "Votre document « " + typeDocument + " » n'a pas pu être validé.\n" +
                "Raison : " + (raison == null ? "non précisée" : raison) + "\n\n" +
                suite +
                "L'équipe School Driva");
    }
}

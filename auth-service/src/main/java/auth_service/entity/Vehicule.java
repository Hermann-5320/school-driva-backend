package auth_service.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "vehicules")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Vehicule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "chauffeur_id", unique = true, nullable = false)
    private Chauffeur chauffeur;

    @Column(nullable = false)
    private String type;

    private String marque;
    private String modele;
    private String couleur;
    private Integer annee;

    @Column(nullable = false)
    private String immatriculation;
}
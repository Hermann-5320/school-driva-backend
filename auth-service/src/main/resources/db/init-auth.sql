-- ════════════════════════════════════════════════════════════════
-- SCHOOL DRIVA — schéma "auth" (auth-service)
-- Exécuté à chaque démarrage (spring.sql.init) : script IDEMPOTENT.
-- Fonctionne sur une base neuve (Render, Neon) comme sur une base
-- créée avec l'ancien script (colonnes ajoutées si absentes).
-- ════════════════════════════════════════════════════════════════

CREATE SCHEMA IF NOT EXISTS auth;

CREATE TABLE IF NOT EXISTS auth.villes (
  id         BIGSERIAL PRIMARY KEY,
  nom        VARCHAR(100) NOT NULL,
  region     VARCHAR(100),
  active     BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS auth.utilisateurs (
  id                  BIGSERIAL PRIMARY KEY,
  email               VARCHAR(255) UNIQUE NOT NULL,
  mot_de_passe        VARCHAR(255) NOT NULL,
  role                VARCHAR(20) NOT NULL CHECK (role IN ('PASSAGER','CHAUFFEUR','ADMIN')),
  statut              VARCHAR(20) DEFAULT 'ACTIF' CHECK (statut IN ('ACTIF','BLOQUE')),
  created_at          TIMESTAMP DEFAULT NOW(),
  tentatives_echouees INT DEFAULT 0,
  verrouille_jusqu_a  TIMESTAMP,
  code_reset          VARCHAR(100),
  code_reset_expire   TIMESTAMP,
  doit_changer_mdp    BOOLEAN DEFAULT FALSE
);
-- Mises à niveau d'une base existante
ALTER TABLE auth.utilisateurs ALTER COLUMN code_reset TYPE VARCHAR(100);
ALTER TABLE auth.utilisateurs ADD COLUMN IF NOT EXISTS doit_changer_mdp BOOLEAN DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS auth.passagers (
  id             BIGSERIAL PRIMARY KEY,
  utilisateur_id BIGINT UNIQUE NOT NULL REFERENCES auth.utilisateurs(id),
  nom            VARCHAR(100) NOT NULL,
  prenom         VARCHAR(100) NOT NULL,
  telephone      VARCHAR(20) NOT NULL,
  ville_id       BIGINT REFERENCES auth.villes(id),
  created_at     TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS auth.chauffeurs (
  id             BIGSERIAL PRIMARY KEY,
  utilisateur_id BIGINT UNIQUE NOT NULL REFERENCES auth.utilisateurs(id),
  nom            VARCHAR(100) NOT NULL,
  prenom         VARCHAR(100) NOT NULL,
  telephone      VARCHAR(20) NOT NULL,
  ville_id       BIGINT REFERENCES auth.villes(id),
  numero_mtn     VARCHAR(20),
  numero_orange  VARCHAR(20),
  statut         VARCHAR(20) DEFAULT 'EN_ATTENTE' CHECK (statut IN ('EN_ATTENTE','ACTIF','BLOQUE')),
  note_moyenne   DECIMAL(3,2) DEFAULT 0,
  nb_courses     INT DEFAULT 0,
  km_total       DECIMAL(10,2) DEFAULT 0,
  en_ligne       BOOLEAN DEFAULT FALSE,
  created_at     TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS auth.vehicules (
  id              BIGSERIAL PRIMARY KEY,
  chauffeur_id    BIGINT UNIQUE NOT NULL REFERENCES auth.chauffeurs(id),
  type            VARCHAR(10) NOT NULL CHECK (type IN ('VOITURE','MOTO')),
  marque          VARCHAR(100),
  modele          VARCHAR(100),
  couleur         VARCHAR(50),
  annee           INT,
  immatriculation VARCHAR(20) UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS auth.documents (
  id           BIGSERIAL PRIMARY KEY,
  chauffeur_id BIGINT NOT NULL REFERENCES auth.chauffeurs(id),
  type         VARCHAR(30) NOT NULL CHECK (type IN (
    'CNI_RECTO','CNI_VERSO','SELFIE_CNI',
    'PERMIS','PLAN_DOMICILE','CARTE_GRISE',
    'ASSURANCE','VISITE_TECHNIQUE',
    'PHOTOS_VEHICULE','CNI_GARANT','CONTACTS_GARANT'
  )),
  statut       VARCHAR(20) DEFAULT 'EN_ATTENTE' CHECK (statut IN ('EN_ATTENTE','VALIDE','REJETE')),
  chemin       VARCHAR(255),
  raison_rejet VARCHAR(500),
  expiration   DATE,
  updated_at   TIMESTAMP DEFAULT NOW(),
  UNIQUE (chauffeur_id, type)
);

-- Contenu des documents (le disque Render gratuit est éphémère)
CREATE TABLE IF NOT EXISTS auth.document_fichiers (
  document_id  BIGINT PRIMARY KEY REFERENCES auth.documents(id) ON DELETE CASCADE,
  content_type VARCHAR(100) NOT NULL,
  contenu      BYTEA NOT NULL
);

CREATE TABLE IF NOT EXISTS auth.favoris (
  id          BIGSERIAL PRIMARY KEY,
  passager_id BIGINT NOT NULL REFERENCES auth.passagers(id),
  label       VARCHAR(50) NOT NULL,
  adresse     VARCHAR(255) NOT NULL,
  lat         DECIMAL(10,8),
  lng         DECIMAL(11,8)
);

CREATE TABLE IF NOT EXISTS auth.admins (
  id             BIGSERIAL PRIMARY KEY,
  utilisateur_id BIGINT UNIQUE NOT NULL REFERENCES auth.utilisateurs(id),
  nom            VARCHAR(200) NOT NULL,
  role           VARCHAR(20) DEFAULT 'ADMIN' CHECK (role IN ('SUPER_ADMIN','ADMIN')),
  statut         VARCHAR(20) DEFAULT 'ACTIF' CHECK (statut IN ('ACTIF','BLOQUE')),
  created_by     BIGINT REFERENCES auth.admins(id),
  created_at     TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS auth.journal_admin (
  id         BIGSERIAL PRIMARY KEY,
  admin_id   BIGINT NOT NULL REFERENCES auth.admins(id),
  action     VARCHAR(500) NOT NULL,
  entite     VARCHAR(100),
  entite_id  BIGINT,
  created_at TIMESTAMP DEFAULT NOW()
);

-- Points campus pré-enregistrés (destinations rapides pour les étudiants)
CREATE TABLE IF NOT EXISTS auth.points_campus (
  id        BIGSERIAL PRIMARY KEY,
  ville_id  BIGINT NOT NULL REFERENCES auth.villes(id),
  nom       VARCHAR(150) NOT NULL,
  categorie VARCHAR(60),
  adresse   VARCHAR(255),
  lat       DECIMAL(10,8) NOT NULL,
  lng       DECIMAL(11,8) NOT NULL,
  actif     BOOLEAN DEFAULT TRUE,
  UNIQUE (ville_id, nom)
);

CREATE INDEX IF NOT EXISTS idx_documents_chauffeur ON auth.documents(chauffeur_id);
CREATE INDEX IF NOT EXISTS idx_campus_ville ON auth.points_campus(ville_id);

-- Centre de chaque ville (rattachement d'une course à la ville desservie)
ALTER TABLE auth.villes ADD COLUMN IF NOT EXISTS lat DECIMAL(10,8);
ALTER TABLE auth.villes ADD COLUMN IF NOT EXISTS lng DECIMAL(11,8);

-- ─── Données initiales (insérées une seule fois) ───────────────
INSERT INTO auth.villes (nom, region, active)
SELECT v.nom, v.region, v.active FROM (VALUES
  ('Yaoundé',   'Centre',     TRUE),
  ('Douala',    'Littoral',   TRUE),
  ('Bafoussam', 'Ouest',      FALSE),
  ('Garoua',    'Nord',       FALSE),
  ('Bamenda',   'Nord-Ouest', FALSE)
) AS v(nom, region, active)
WHERE NOT EXISTS (SELECT 1 FROM auth.villes x WHERE x.nom = v.nom);

-- Compte super-admin initial (mot de passe à changer dès la 1re connexion)
INSERT INTO auth.utilisateurs (email, mot_de_passe, role, statut, doit_changer_mdp)
SELECT 'schooldriva@gmail.com',
       '$2a$10$DCmtl4wvXqmhonM01kjhuuH3/s5H50umhmmuvshSsAwY9H/t4zog.',
       'ADMIN', 'ACTIF', TRUE
WHERE NOT EXISTS (SELECT 1 FROM auth.utilisateurs WHERE email = 'schooldriva@gmail.com');

INSERT INTO auth.admins (utilisateur_id, nom, role, statut)
SELECT u.id, 'Admin Principal', 'SUPER_ADMIN', 'ACTIF'
FROM auth.utilisateurs u
WHERE u.email = 'schooldriva@gmail.com'
  AND NOT EXISTS (SELECT 1 FROM auth.admins a WHERE a.utilisateur_id = u.id);

-- Points campus (coordonnées approximatives, ajustables par l'admin)
INSERT INTO auth.points_campus (ville_id, nom, categorie, adresse, lat, lng)
SELECT v.id, p.nom, p.categorie, p.adresse, p.lat, p.lng
FROM (VALUES
  ('Yaoundé', 'Université de Yaoundé I', 'Université', 'Ngoa-Ekellé', 3.85890000, 11.49950000),
  ('Yaoundé', 'École Nationale Supérieure Polytechnique', 'Grande école', 'Melen', 3.86330000, 11.49880000),
  ('Yaoundé', 'École Normale Supérieure', 'Grande école', 'Ngoa-Ekellé', 3.86550000, 11.49930000),
  ('Yaoundé', 'Cité universitaire de Ngoa-Ekellé', 'Cité universitaire', 'Ngoa-Ekellé', 3.85700000, 11.49900000),
  ('Yaoundé', 'Université de Yaoundé II', 'Université', 'Soa', 3.97860000, 11.59530000),
  ('Douala',  'Université de Douala', 'Université', 'Ndong Bong', 4.04580000, 9.73000000)
) AS p(ville, nom, categorie, adresse, lat, lng)
JOIN auth.villes v ON v.nom = p.ville
WHERE NOT EXISTS (SELECT 1 FROM auth.points_campus x WHERE x.ville_id = v.id AND x.nom = p.nom);

-- Coordonnées des villes (renseignées une seule fois, modifiables ensuite en base)
UPDATE auth.villes SET lat = 3.84800000, lng = 11.50210000 WHERE nom = 'Yaoundé'   AND lat IS NULL;
UPDATE auth.villes SET lat = 4.05110000, lng = 9.76790000  WHERE nom = 'Douala'    AND lat IS NULL;
UPDATE auth.villes SET lat = 5.47780000, lng = 10.41760000 WHERE nom = 'Bafoussam' AND lat IS NULL;
UPDATE auth.villes SET lat = 9.30170000, lng = 13.39210000 WHERE nom = 'Garoua'    AND lat IS NULL;
UPDATE auth.villes SET lat = 5.96310000, lng = 10.15910000 WHERE nom = 'Bamenda'   AND lat IS NULL;

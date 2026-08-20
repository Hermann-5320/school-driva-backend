-- ─────────────────────────────────────────
-- SCHOOL DRIVA — Script PostgreSQL complet
-- 17 tables — Version finale
-- ─────────────────────────────────────────

-- ─── VILLES ───────────────────────────────
CREATE TABLE villes (
  id         BIGSERIAL PRIMARY KEY,
  nom        VARCHAR(100) NOT NULL,
  region     VARCHAR(100),
  active     BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMP DEFAULT NOW()
);

-- ─── UTILISATEURS ─────────────────────────
CREATE TABLE utilisateurs (
  id           BIGSERIAL PRIMARY KEY,
  email        VARCHAR(255) UNIQUE NOT NULL,
  mot_de_passe VARCHAR(255) NOT NULL,
  role         VARCHAR(20) NOT NULL CHECK (role IN ('PASSAGER','CHAUFFEUR','ADMIN')),
  statut       VARCHAR(20) DEFAULT 'ACTIF' CHECK (statut IN ('ACTIF','BLOQUE')),
  created_at   TIMESTAMP DEFAULT NOW()
);

-- ─── PASSAGERS ────────────────────────────
CREATE TABLE passagers (
  id             BIGSERIAL PRIMARY KEY,
  utilisateur_id BIGINT UNIQUE NOT NULL REFERENCES utilisateurs(id),
  nom            VARCHAR(100) NOT NULL,
  prenom         VARCHAR(100) NOT NULL,
  telephone      VARCHAR(20) NOT NULL,
  ville_id       BIGINT REFERENCES villes(id),
  created_at     TIMESTAMP DEFAULT NOW()
);

-- ─── CHAUFFEURS ───────────────────────────
CREATE TABLE chauffeurs (
  id             BIGSERIAL PRIMARY KEY,
  utilisateur_id BIGINT UNIQUE NOT NULL REFERENCES utilisateurs(id),
  nom            VARCHAR(100) NOT NULL,
  prenom         VARCHAR(100) NOT NULL,
  telephone      VARCHAR(20) NOT NULL,
  ville_id       BIGINT REFERENCES villes(id),
  numero_mtn     VARCHAR(20),
  numero_orange  VARCHAR(20),
  statut         VARCHAR(20) DEFAULT 'EN_ATTENTE' CHECK (statut IN ('EN_ATTENTE','ACTIF','BLOQUE')),
  note_moyenne   DECIMAL(3,2) DEFAULT 0,
  nb_courses     INT DEFAULT 0,
  km_total       DECIMAL(10,2) DEFAULT 0,
  en_ligne       BOOLEAN DEFAULT FALSE,
  created_at     TIMESTAMP DEFAULT NOW()
);

-- ─── VEHICULES ────────────────────────────
CREATE TABLE vehicules (
  id              BIGSERIAL PRIMARY KEY,
  chauffeur_id    BIGINT UNIQUE NOT NULL REFERENCES chauffeurs(id),
  type            VARCHAR(10) NOT NULL CHECK (type IN ('VOITURE','MOTO')),
  marque          VARCHAR(100),
  modele          VARCHAR(100),
  couleur         VARCHAR(50),
  annee           INT,
  immatriculation VARCHAR(20) UNIQUE NOT NULL
);

-- ─── DOCUMENTS ────────────────────────────
CREATE TABLE documents (
  id           BIGSERIAL PRIMARY KEY,
  chauffeur_id BIGINT NOT NULL REFERENCES chauffeurs(id),
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

-- ─── COURSES ──────────────────────────────
CREATE TABLE courses (
  id              BIGSERIAL PRIMARY KEY,
  passager_id     BIGINT NOT NULL REFERENCES passagers(id),
  chauffeur_id    BIGINT REFERENCES chauffeurs(id),
  ville_id        BIGINT REFERENCES villes(id),
  type_vehicule   VARCHAR(10) NOT NULL CHECK (type_vehicule IN ('VOITURE','MOTO')),
  depart_adresse  VARCHAR(255) NOT NULL,
  depart_lat      DECIMAL(10,8),
  depart_lng      DECIMAL(11,8),
  arrivee_adresse VARCHAR(255) NOT NULL,
  arrivee_lat     DECIMAL(10,8),
  arrivee_lng     DECIMAL(11,8),
  distance_km     DECIMAL(6,2),
  duree_min       INT,
  prix_estime     DECIMAL(10,2),
  prix_final      DECIMAL(10,2),
  statut          VARCHAR(20) DEFAULT 'EN_ATTENTE' CHECK (statut IN (
    'EN_ATTENTE','ACCEPTEE','EN_ROUTE',
    'ARRIVEE','DEMARREE','TERMINEE','ANNULEE'
  )),
  annulee_par     VARCHAR(10) CHECK (annulee_par IN ('PASSAGER','CHAUFFEUR','ADMIN')),
  partage_token   VARCHAR(100),
  created_at      TIMESTAMP DEFAULT NOW(),
  started_at      TIMESTAMP,
  ended_at        TIMESTAMP,
  CONSTRAINT chk_prix_annulation CHECK (
    statut != 'ANNULEE' OR prix_final IS NULL
  )
);

-- ─── PAIEMENTS ────────────────────────────
CREATE TABLE paiements (
  id             BIGSERIAL PRIMARY KEY,
  course_id      BIGINT UNIQUE NOT NULL REFERENCES courses(id),
  mode           VARCHAR(10) NOT NULL CHECK (mode IN ('LIQUIDE','MTN','ORANGE')),
  montant        DECIMAL(10,2) NOT NULL,
  frais          DECIMAL(10,2) DEFAULT 0,
  commission     DECIMAL(10,2) NOT NULL,
  gain_chauffeur DECIMAL(10,2) NOT NULL,
  statut         VARCHAR(20) DEFAULT 'EN_ATTENTE' CHECK (statut IN ('EN_ATTENTE','CONFIRME','REJETE')),
  confirme_at    TIMESTAMP,
  CONSTRAINT chk_gain CHECK (gain_chauffeur = montant - commission),
  CONSTRAINT chk_commission_positive CHECK (commission >= 0),
  CONSTRAINT chk_gain_positive CHECK (gain_chauffeur >= 0)
);

-- ─── NOTATIONS ────────────────────────────
CREATE TABLE notations (
  id           BIGSERIAL PRIMARY KEY,
  course_id    BIGINT UNIQUE NOT NULL REFERENCES courses(id),
  passager_id  BIGINT NOT NULL REFERENCES passagers(id),
  chauffeur_id BIGINT NOT NULL REFERENCES chauffeurs(id),
  note         INT CHECK (note BETWEEN 1 AND 5),
  commentaire  TEXT,
  securite_ok  BOOLEAN,
  tags         VARCHAR(500),
  created_at   TIMESTAMP DEFAULT NOW()
);

-- ─── PORTEFEUILLES ────────────────────────
CREATE TABLE portefeuilles (
  id           BIGSERIAL PRIMARY KEY,
  chauffeur_id BIGINT UNIQUE NOT NULL REFERENCES chauffeurs(id),
  solde        DECIMAL(10,2) DEFAULT 0,
  updated_at   TIMESTAMP DEFAULT NOW(),
  CONSTRAINT chk_solde_positif CHECK (solde >= 0)
);

-- ─── RECHARGES ────────────────────────────
CREATE TABLE recharges (
  id           BIGSERIAL PRIMARY KEY,
  chauffeur_id BIGINT NOT NULL REFERENCES chauffeurs(id),
  montant      DECIMAL(10,2) NOT NULL,
  operateur    VARCHAR(10) NOT NULL CHECK (operateur IN ('MTN','ORANGE')),
  statut       VARCHAR(20) DEFAULT 'EN_ATTENTE' CHECK (statut IN ('EN_ATTENTE','VALIDEE','REJETEE')),
  capture      VARCHAR(255),
  raison_rejet VARCHAR(500),
  valide_par   BIGINT REFERENCES utilisateurs(id),
  created_at   TIMESTAMP DEFAULT NOW()
);

-- ─── TRANSACTIONS ─────────────────────────
CREATE TABLE transactions (
  id              BIGSERIAL PRIMARY KEY,
  portefeuille_id BIGINT NOT NULL REFERENCES portefeuilles(id),
  type            VARCHAR(20) NOT NULL CHECK (type IN ('COMMISSION','RECHARGE')),
  montant         DECIMAL(10,2) NOT NULL,
  course_id       BIGINT REFERENCES courses(id),
  recharge_id     BIGINT REFERENCES recharges(id),
  created_at      TIMESTAMP DEFAULT NOW(),
  CONSTRAINT chk_source CHECK (
    (course_id IS NOT NULL AND recharge_id IS NULL) OR
    (course_id IS NULL AND recharge_id IS NOT NULL)
  )
);

-- ─── FAVORIS ──────────────────────────────
CREATE TABLE favoris (
  id          BIGSERIAL PRIMARY KEY,
  passager_id BIGINT NOT NULL REFERENCES passagers(id),
  label       VARCHAR(50) NOT NULL,
  adresse     VARCHAR(255) NOT NULL,
  lat         DECIMAL(10,8),
  lng         DECIMAL(11,8)
);

-- ─── DESTINATIONS RECENTES ────────────────
CREATE TABLE destinations_recentes (
  id          BIGSERIAL PRIMARY KEY,
  passager_id BIGINT NOT NULL REFERENCES passagers(id),
  adresse     VARCHAR(255) NOT NULL,
  lat         DECIMAL(10,8),
  lng         DECIMAL(11,8),
  created_at  TIMESTAMP DEFAULT NOW()
);

-- ─── ADMINS ───────────────────────────────
CREATE TABLE admins (
  id             BIGSERIAL PRIMARY KEY,
  utilisateur_id BIGINT UNIQUE NOT NULL REFERENCES utilisateurs(id),
  nom            VARCHAR(200) NOT NULL,
  role           VARCHAR(20) DEFAULT 'ADMIN' CHECK (role IN ('SUPER_ADMIN','ADMIN')),
  statut         VARCHAR(20) DEFAULT 'ACTIF' CHECK (statut IN ('ACTIF','BLOQUE')),
  created_by     BIGINT REFERENCES admins(id),
  created_at     TIMESTAMP DEFAULT NOW()
);

-- ─── JOURNAL ADMIN ────────────────────────
CREATE TABLE journal_admin (
  id         BIGSERIAL PRIMARY KEY,
  admin_id   BIGINT NOT NULL REFERENCES admins(id),
  action     VARCHAR(500) NOT NULL,
  entite     VARCHAR(100),
  entite_id  BIGINT,
  created_at TIMESTAMP DEFAULT NOW()
);

-- ─── NOTIFICATIONS ────────────────────────
CREATE TABLE notifications (
  id             BIGSERIAL PRIMARY KEY,
  utilisateur_id BIGINT NOT NULL REFERENCES utilisateurs(id),
  titre          VARCHAR(255) NOT NULL,
  message        TEXT NOT NULL,
  lu             BOOLEAN DEFAULT FALSE,
  created_at     TIMESTAMP DEFAULT NOW()
);

-- ─── CONFIG ───────────────────────────────
CREATE TABLE config (
  cle         VARCHAR(100) PRIMARY KEY,
  valeur      VARCHAR(255) NOT NULL,
  description VARCHAR(500),
  updated_by  BIGINT REFERENCES admins(id),
  updated_at  TIMESTAMP DEFAULT NOW()
);

-- ─── INDEX ────────────────────────────────
CREATE INDEX idx_courses_passager      ON courses(passager_id);
CREATE INDEX idx_courses_chauffeur     ON courses(chauffeur_id);
CREATE INDEX idx_courses_statut        ON courses(statut);
CREATE INDEX idx_courses_ville         ON courses(ville_id);
CREATE INDEX idx_transactions_pf       ON transactions(portefeuille_id);
CREATE INDEX idx_notations_chauffeur   ON notations(chauffeur_id);
CREATE INDEX idx_documents_chauffeur   ON documents(chauffeur_id);
CREATE INDEX idx_recharges_chauffeur   ON recharges(chauffeur_id);
CREATE INDEX idx_notif_utilisateur     ON notifications(utilisateur_id);
CREATE INDEX idx_destinations_passager ON destinations_recentes(passager_id);

-- ─── DONNÉES INITIALES ────────────────────
INSERT INTO villes (nom, region, active) VALUES
  ('Yaoundé',   'Centre',    TRUE),
  ('Douala',    'Littoral',  TRUE),
  ('Bafoussam', 'Ouest',     FALSE),
  ('Garoua',    'Nord',      FALSE),
  ('Bamenda',   'Nord-Ouest',FALSE);

INSERT INTO config (cle, valeur, description) VALUES
  ('COMMISSION_TAUX', '15',   'Taux de commission en %'),
  ('FRAIS_BAS',       '54',   'Frais retrait < seuil en FCFA'),
  ('FRAIS_HAUT',      '100',  'Frais retrait >= seuil en FCFA'),
  ('SEUIL_FRAIS',     '5000', 'Seuil frais de retrait en FCFA'),
  ('TIMER_COURSE',    '15',   'Compte à rebours demande en secondes');
  -- Compte admin par défaut
INSERT INTO utilisateurs (email, mot_de_passe, role, statut)
VALUES (
  'admin@schooldriva.cm',
  '$2a$10$DCmtl4wvXqmhonM01kjhuuH3/s5H50umhmmuvshSsAwY9H/t4zog.',
  'ADMIN',
  'ACTIF'
);

INSERT INTO admins (utilisateur_id, nom, role, statut)
VALUES (
  (SELECT id FROM utilisateurs WHERE email = 'admin@schooldriva.cm'),
  'Admin Principal',
  'SUPER_ADMIN',
  'ACTIF'
);

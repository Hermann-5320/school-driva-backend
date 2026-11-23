-- ════════════════════════════════════════════════════════════════
-- SCHOOL DRIVA — schéma "course" (course-service)
-- Exécuté à chaque démarrage : script IDEMPOTENT.
-- Pas de clé étrangère vers les autres schémas : chaque microservice
-- possède ses données (passager_id / chauffeur_id sont des références logiques).
-- ════════════════════════════════════════════════════════════════

CREATE SCHEMA IF NOT EXISTS course;

CREATE TABLE IF NOT EXISTS course.courses (
  id              BIGSERIAL PRIMARY KEY,
  passager_id     BIGINT NOT NULL,
  chauffeur_id    BIGINT,
  ville_id        BIGINT,
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
    'EN_ATTENTE','ACCEPTEE','EN_ROUTE','ARRIVEE','DEMARREE','TERMINEE','ANNULEE'
  )),
  annulee_par     VARCHAR(10),
  partage_token   VARCHAR(100),
  created_at      TIMESTAMP DEFAULT NOW(),
  started_at      TIMESTAMP,
  ended_at        TIMESTAMP,
  CONSTRAINT chk_prix_annulation CHECK (statut != 'ANNULEE' OR prix_final IS NULL)
);

-- Mises à niveau d'une base existante
ALTER TABLE course.courses ADD COLUMN IF NOT EXISTS chauffeur_lat DECIMAL(10,8);
ALTER TABLE course.courses ADD COLUMN IF NOT EXISTS chauffeur_lng DECIMAL(11,8);
ALTER TABLE course.courses ADD COLUMN IF NOT EXISTS position_maj_at TIMESTAMP;
-- Nouvelle valeur SYSTEME (annulation automatique d'une course sans chauffeur)
ALTER TABLE course.courses DROP CONSTRAINT IF EXISTS courses_annulee_par_check;
ALTER TABLE course.courses ADD CONSTRAINT courses_annulee_par_check
  CHECK (annulee_par IS NULL OR annulee_par IN ('PASSAGER','CHAUFFEUR','ADMIN','SYSTEME'));

CREATE UNIQUE INDEX IF NOT EXISTS ux_courses_partage_token ON course.courses(partage_token);
CREATE INDEX IF NOT EXISTS idx_courses_passager  ON course.courses(passager_id);
CREATE INDEX IF NOT EXISTS idx_courses_chauffeur ON course.courses(chauffeur_id);
CREATE INDEX IF NOT EXISTS idx_courses_statut    ON course.courses(statut);
CREATE INDEX IF NOT EXISTS idx_courses_ville     ON course.courses(ville_id);

-- Paiement d'une course (déclaré par le passager, confirmé par le chauffeur)
CREATE TABLE IF NOT EXISTS course.paiements (
  id             BIGSERIAL PRIMARY KEY,
  course_id      BIGINT UNIQUE NOT NULL REFERENCES course.courses(id),
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

CREATE TABLE IF NOT EXISTS course.notations (
  id           BIGSERIAL PRIMARY KEY,
  course_id    BIGINT UNIQUE NOT NULL REFERENCES course.courses(id),
  passager_id  BIGINT NOT NULL,
  chauffeur_id BIGINT NOT NULL,
  note         INT CHECK (note BETWEEN 1 AND 5),
  commentaire  TEXT,
  securite_ok  BOOLEAN,
  tags         VARCHAR(500),
  created_at   TIMESTAMP DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_notations_chauffeur ON course.notations(chauffeur_id);

-- Paramètres modifiables depuis la page Paramètres de l'admin
CREATE TABLE IF NOT EXISTS course.parametres_systeme (
  cle         VARCHAR(100) PRIMARY KEY,
  valeur      VARCHAR(255) NOT NULL,
  description VARCHAR(500),
  updated_at  TIMESTAMP DEFAULT NOW()
);

INSERT INTO course.parametres_systeme (cle, valeur, description) VALUES
  ('COMMISSION_POURCENTAGE',  '15',  'Commission School Driva prélevée sur chaque course (%)'),
  ('TARIF_VOITURE_KM',        '500', 'Tarif par km en voiture (FCFA)'),
  ('TARIF_MOTO_KM',           '300', 'Tarif par km en moto (FCFA)'),
  ('PRIX_MINIMUM',            '500', 'Prix minimum d''une course (FCFA)'),
  ('SOLDE_MINIMUM_CHAUFFEUR', '500', 'Solde minimum du portefeuille pour accepter une course (FCFA)'),
  ('DELAI_RECHERCHE_MIN',     '10',  'Délai avant annulation automatique d''une course sans chauffeur (minutes)'),
  ('DISTANCE_MAX_KM',         '60',  'Distance maximale d''une course (km)'),
  ('TIMER_COURSE',            '15',  'Temps laissé au chauffeur pour accepter une demande (secondes)'),
  ('RAYON_DESSERTE_KM',       '30',  'Rayon autour du centre d''une ville dans lequel les départs sont acceptés (km)'),
  ('ITINERAIRE_PREFERENCE',   '1',   'Calcul des trajets : 1 = plus court, 2 = plus rapide, 3 = recommandé')
ON CONFLICT (cle) DO NOTHING;

-- Paramètres d'une ancienne version, jamais utilisés par l'application
DELETE FROM course.parametres_systeme WHERE cle IN ('FRAIS_BAS', 'FRAIS_HAUT', 'SEUIL_FRAIS');

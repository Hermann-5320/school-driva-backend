-- ════════════════════════════════════════════════════════════════
-- SCHOOL DRIVA — schéma "paiement" (paiement-service)
-- Exécuté à chaque démarrage : script IDEMPOTENT.
-- ════════════════════════════════════════════════════════════════

CREATE SCHEMA IF NOT EXISTS paiement;

CREATE TABLE IF NOT EXISTS paiement.portefeuilles (
  id           BIGSERIAL PRIMARY KEY,
  chauffeur_id BIGINT UNIQUE NOT NULL,
  solde        DECIMAL(10,2) DEFAULT 0,
  updated_at   TIMESTAMP DEFAULT NOW(),
  CONSTRAINT chk_solde_positif CHECK (solde >= 0)
);

CREATE TABLE IF NOT EXISTS paiement.recharges (
  id                    BIGSERIAL PRIMARY KEY,
  chauffeur_id          BIGINT NOT NULL,
  montant               DECIMAL(10,2) NOT NULL,
  operateur             VARCHAR(10) NOT NULL CHECK (operateur IN ('MTN','ORANGE')),
  statut                VARCHAR(20) DEFAULT 'EN_ATTENTE' CHECK (statut IN ('EN_ATTENTE','VALIDEE','REJETEE')),
  capture               VARCHAR(255),
  raison_rejet          VARCHAR(500),
  valide_par            BIGINT,
  created_at            TIMESTAMP DEFAULT NOW(),
  pay_token             VARCHAR(255),
  reference_transaction VARCHAR(255),
  confirmed_at          TIMESTAMP
);
ALTER TABLE paiement.recharges ADD COLUMN IF NOT EXISTS telephone VARCHAR(20);

CREATE TABLE IF NOT EXISTS paiement.transactions (
  id              BIGSERIAL PRIMARY KEY,
  portefeuille_id BIGINT NOT NULL REFERENCES paiement.portefeuilles(id),
  type            VARCHAR(20) NOT NULL CHECK (type IN ('COMMISSION','RECHARGE')),
  montant         DECIMAL(10,2) NOT NULL,
  course_id       BIGINT,
  recharge_id     BIGINT REFERENCES paiement.recharges(id),
  created_at      TIMESTAMP DEFAULT NOW(),
  CONSTRAINT chk_source CHECK (
    (course_id IS NOT NULL AND recharge_id IS NULL) OR
    (course_id IS NULL AND recharge_id IS NOT NULL)
  )
);

CREATE INDEX IF NOT EXISTS idx_transactions_pf     ON paiement.transactions(portefeuille_id);
CREATE INDEX IF NOT EXISTS idx_recharges_chauffeur ON paiement.recharges(chauffeur_id);
CREATE INDEX IF NOT EXISTS idx_recharges_paytoken  ON paiement.recharges(pay_token);

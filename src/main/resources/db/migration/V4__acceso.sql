-- ============================================================
--  Acceso de las trabajadoras y recuperación de contraseña
-- ============================================================

-- Las manicuristas entran con su cédula, no con correo: muchas no
-- tienen uno que revisen, pero la cédula se la saben de memoria.
ALTER TABLE usuario ADD COLUMN documento VARCHAR(30);
ALTER TABLE usuario ALTER COLUMN email DROP NOT NULL;

-- La cédula es única en todo el sistema, no por empresa
CREATE UNIQUE INDEX ux_usuario_documento ON usuario(documento)
    WHERE documento IS NOT NULL;

-- Debe existir al menos una forma de identificarse
ALTER TABLE usuario ADD CONSTRAINT usuario_con_identificador
    CHECK (email IS NOT NULL OR documento IS NOT NULL);

CREATE TABLE token_clave (
    id         BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    token      VARCHAR(80) NOT NULL UNIQUE,
    vence_en   TIMESTAMPTZ NOT NULL,
    usado      BOOLEAN NOT NULL DEFAULT FALSE,
    creado_en  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_token_usuario ON token_clave(usuario_id);

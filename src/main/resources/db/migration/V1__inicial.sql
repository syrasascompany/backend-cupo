-- ============================================================
--  Modelo inicial. Multi-empresa desde el primer día: todas las
--  tablas cuelgan de empresa_id aunque hoy solo exista un cliente.
--  Migrar esto después sale mucho más caro que dejarlo puesto ya.
-- ============================================================

CREATE TABLE empresa (
    id                BIGSERIAL PRIMARY KEY,
    nombre            VARCHAR(160) NOT NULL,
    slug              VARCHAR(80)  NOT NULL UNIQUE,
    telefono_wa       VARCHAR(20),
    zona_horaria      VARCHAR(60)  NOT NULL DEFAULT 'America/Bogota',
    plan              VARCHAR(20)  NOT NULL DEFAULT 'ESENCIAL',
    max_profesionales INT          NOT NULL DEFAULT 2,
    activa            BOOLEAN      NOT NULL DEFAULT TRUE,
    creada_en         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE profesional (
    id         BIGSERIAL PRIMARY KEY,
    empresa_id BIGINT NOT NULL REFERENCES empresa(id) ON DELETE CASCADE,
    nombre     VARCHAR(120) NOT NULL,
    telefono   VARCHAR(20),
    color      VARCHAR(9),
    activo     BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX ix_profesional_empresa ON profesional(empresa_id);

CREATE TABLE usuario (
    id             BIGSERIAL PRIMARY KEY,
    empresa_id     BIGINT REFERENCES empresa(id) ON DELETE CASCADE, -- nulo solo para SUPERADMIN
    email          VARCHAR(160) NOT NULL UNIQUE,
    clave_hash     VARCHAR(120) NOT NULL,
    nombre         VARCHAR(120) NOT NULL,
    rol            VARCHAR(20)  NOT NULL CHECK (rol IN ('SUPERADMIN','ADMIN','TRABAJADORA')),
    profesional_id BIGINT REFERENCES profesional(id) ON DELETE SET NULL,
    activo         BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_usuario_empresa ON usuario(empresa_id);

CREATE TABLE servicio (
    id              BIGSERIAL PRIMARY KEY,
    empresa_id      BIGINT NOT NULL REFERENCES empresa(id) ON DELETE CASCADE,
    nombre          VARCHAR(140) NOT NULL,
    precio_centavos BIGINT NOT NULL DEFAULT 0,
    activo          BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX ix_servicio_empresa ON servicio(empresa_id);

-- La duración depende del servicio Y de quién lo hace.
-- Asumir una duración única es la causa más común de citas cruzadas.
CREATE TABLE servicio_profesional (
    id             BIGSERIAL PRIMARY KEY,
    servicio_id    BIGINT NOT NULL REFERENCES servicio(id) ON DELETE CASCADE,
    profesional_id BIGINT NOT NULL REFERENCES profesional(id) ON DELETE CASCADE,
    duracion_min   INT    NOT NULL CHECK (duracion_min > 0),
    UNIQUE (servicio_id, profesional_id)
);

CREATE TABLE horario_base (
    id             BIGSERIAL PRIMARY KEY,
    profesional_id BIGINT NOT NULL REFERENCES profesional(id) ON DELETE CASCADE,
    dia_semana     SMALLINT NOT NULL CHECK (dia_semana BETWEEN 1 AND 7), -- 1=lunes, 7=domingo
    hora_inicio    TIME NOT NULL,
    hora_fin       TIME NOT NULL,
    CHECK (hora_fin > hora_inicio)
);
CREATE INDEX ix_horario_prof_dia ON horario_base(profesional_id, dia_semana);

-- Permisos, ausencias y cambios puntuales sobre el horario normal.
-- AUSENCIA: no viene en todo el día.
-- BLOQUEO: no está disponible entre dos horas (permiso, almuerzo largo).
-- CAMBIO_HORARIO: ese día trabaja en otro rango, reemplaza al horario base.
CREATE TABLE excepcion_horario (
    id             BIGSERIAL PRIMARY KEY,
    profesional_id BIGINT NOT NULL REFERENCES profesional(id) ON DELETE CASCADE,
    fecha          DATE NOT NULL,
    tipo           VARCHAR(20) NOT NULL CHECK (tipo IN ('AUSENCIA','BLOQUEO','CAMBIO_HORARIO')),
    hora_inicio    TIME,
    hora_fin       TIME,
    motivo         VARCHAR(200)
);
CREATE INDEX ix_excepcion_prof_fecha ON excepcion_horario(profesional_id, fecha);

CREATE TABLE cliente (
    id         BIGSERIAL PRIMARY KEY,
    empresa_id BIGINT NOT NULL REFERENCES empresa(id) ON DELETE CASCADE,
    nombre     VARCHAR(140) NOT NULL,
    telefono   VARCHAR(20)  NOT NULL,
    notas      TEXT,
    creado_en  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (empresa_id, telefono)
);

CREATE TABLE cita (
    id             BIGSERIAL PRIMARY KEY,
    empresa_id     BIGINT NOT NULL REFERENCES empresa(id) ON DELETE CASCADE,
    profesional_id BIGINT NOT NULL REFERENCES profesional(id),
    servicio_id    BIGINT NOT NULL REFERENCES servicio(id),
    cliente_id     BIGINT REFERENCES cliente(id),
    inicio         TIMESTAMPTZ NOT NULL,
    fin            TIMESTAMPTZ NOT NULL,
    estado         VARCHAR(20) NOT NULL DEFAULT 'CONFIRMADA'
                   CHECK (estado IN ('CONFIRMADA','FINALIZADA','CANCELADA','NO_ASISTIO')),
    origen         VARCHAR(20) NOT NULL DEFAULT 'PANEL'
                   CHECK (origen IN ('PANEL','WHATSAPP','WEB')),
    notas          TEXT,
    creada_en      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (fin > inicio)
);
CREATE INDEX ix_cita_prof_inicio ON cita(profesional_id, inicio);
CREATE INDEX ix_cita_empresa_inicio ON cita(empresa_id, inicio);

-- Red de seguridad contra el doble agendamiento. Aunque falle la
-- validación en el código, la base de datos rechaza dos citas vivas
-- que se pisen para la misma profesional.
CREATE EXTENSION IF NOT EXISTS btree_gist;
ALTER TABLE cita ADD CONSTRAINT cita_sin_cruce
    EXCLUDE USING gist (
        profesional_id WITH =,
        tstzrange(inicio, fin) WITH &&
    ) WHERE (estado IN ('CONFIRMADA','FINALIZADA'));

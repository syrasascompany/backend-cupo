-- ============================================================
--  Lista de espera, registro de notificaciones y estado del bot
-- ============================================================

-- Meta identifica cada número con un phone_number_id.
-- Es lo que llega en el webhook y lo que nos dice de qué empresa se trata.
ALTER TABLE empresa ADD COLUMN wa_phone_number_id VARCHAR(40);
ALTER TABLE empresa ADD COLUMN wa_token TEXT;
CREATE UNIQUE INDEX ux_empresa_wa_phone ON empresa(wa_phone_number_id)
    WHERE wa_phone_number_id IS NOT NULL;

CREATE TABLE lista_espera (
    id             BIGSERIAL PRIMARY KEY,
    empresa_id     BIGINT NOT NULL REFERENCES empresa(id) ON DELETE CASCADE,
    cliente_id     BIGINT REFERENCES cliente(id) ON DELETE CASCADE,
    servicio_id    BIGINT NOT NULL REFERENCES servicio(id) ON DELETE CASCADE,
    profesional_id BIGINT REFERENCES profesional(id) ON DELETE SET NULL, -- null = cualquiera
    desde          DATE NOT NULL,
    hasta          DATE NOT NULL,
    telefono       VARCHAR(20) NOT NULL,
    nombre         VARCHAR(140),
    estado         VARCHAR(20) NOT NULL DEFAULT 'ESPERANDO'
                   CHECK (estado IN ('ESPERANDO','AVISADO','TOMO_CUPO','VENCIDO','CANCELADO')),
    avisado_en     TIMESTAMPTZ,
    creado_en      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_espera_busqueda ON lista_espera(empresa_id, servicio_id, estado, desde, hasta);

-- Evita mandar el mismo recordatorio dos veces si la tarea corre repetida
CREATE TABLE notificacion (
    id          BIGSERIAL PRIMARY KEY,
    empresa_id  BIGINT NOT NULL REFERENCES empresa(id) ON DELETE CASCADE,
    cita_id     BIGINT REFERENCES cita(id) ON DELETE CASCADE,
    tipo        VARCHAR(30) NOT NULL,
    destino     VARCHAR(40) NOT NULL,
    estado      VARCHAR(20) NOT NULL DEFAULT 'ENVIADA',
    detalle     TEXT,
    enviada_en  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (cita_id, tipo)
);

-- El bot no tiene memoria: aquí se guarda en qué paso va cada conversación.
CREATE TABLE conversacion (
    id             BIGSERIAL PRIMARY KEY,
    empresa_id     BIGINT NOT NULL REFERENCES empresa(id) ON DELETE CASCADE,
    telefono       VARCHAR(20) NOT NULL,
    paso           VARCHAR(30) NOT NULL DEFAULT 'INICIO',
    servicio_id    BIGINT,
    profesional_id BIGINT,
    fecha          DATE,
    inicio_elegido TIMESTAMPTZ,
    nombre_cliente VARCHAR(140),
    ultimo_mensaje TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (empresa_id, telefono)
);
CREATE INDEX ix_conversacion_actividad ON conversacion(ultimo_mensaje);

-- Ningún mensaje entrante de WhatsApp se procesa dos veces.
CREATE TABLE mensaje_procesado (
    wa_message_id VARCHAR(80) PRIMARY KEY,
    recibido_en   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================
--  Comisiones y método de pago
--
--  Dos cosas que pidió Luxury Nails:
--
--  1. Cada profesional gana un porcentaje de lo que produce.
--     Casi siempre es el 50%, pero no en todos los salones ni
--     para todas, así que va configurable por persona.
--
--  2. Saber con qué pagó cada clienta. Lo marca la trabajadora
--     al finalizar, o el administrador desde la agenda.
-- ============================================================

-- Porcentaje que se le paga a cada profesional sobre lo que produce
ALTER TABLE profesional
    ADD COLUMN comision_pct NUMERIC(5,2) NOT NULL DEFAULT 50.00;

ALTER TABLE profesional
    ADD CONSTRAINT comision_en_rango CHECK (comision_pct >= 0 AND comision_pct <= 100);


-- Con qué pagó la clienta. Queda en nulo hasta que alguien lo marque.
ALTER TABLE cita ADD COLUMN metodo_pago VARCHAR(20);
ALTER TABLE cita ADD COLUMN pagado_en TIMESTAMPTZ;

ALTER TABLE cita ADD CONSTRAINT metodo_pago_valido
    CHECK (metodo_pago IS NULL OR metodo_pago IN
                                  ('EFECTIVO','TRANSFERENCIA','TARJETA','OTRO'));

-- El valor cobrado se guarda en la cita, no se saca del precio actual
-- del servicio: si mañana suben el precio, lo del mes pasado no puede
-- cambiar. La contabilidad tiene que cuadrar con lo que de verdad se cobró.
ALTER TABLE cita ADD COLUMN valor_cobrado_centavos BIGINT;

CREATE INDEX ix_cita_pago ON cita(empresa_id, pagado_en)
    WHERE pagado_en IS NOT NULL;
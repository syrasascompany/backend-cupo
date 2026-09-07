-- ============================================================
--  Pausa del bot cuando contesta una persona
--
--  Con Coexistence, el dueño y el bot comparten el mismo número.
--  Si él está atendiendo a una clienta desde su celular, el bot no
--  puede meterse a mandar el menú de servicios en medio de la
--  conversación. Aquí se guarda hasta cuándo debe quedarse callado.
-- ============================================================

ALTER TABLE conversacion ADD COLUMN pausado_hasta TIMESTAMPTZ;

CREATE INDEX ix_conversacion_pausa ON conversacion(pausado_hasta)
    WHERE pausado_hasta IS NOT NULL;

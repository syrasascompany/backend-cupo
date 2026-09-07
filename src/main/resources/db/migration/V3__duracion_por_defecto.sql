-- ============================================================
--  Duración normal por servicio, y ajuste solo para quien
--  se sale de esa norma.
--
--  Sin esto, dar de alta un salón de 10 chicas con 10 servicios
--  exige teclear 100 duraciones. Nadie termina eso.
-- ============================================================

ALTER TABLE servicio ADD COLUMN duracion_min INT NOT NULL DEFAULT 60;

-- Ahora puede ir en nulo: nulo significa "se demora lo normal"
ALTER TABLE servicio_profesional ALTER COLUMN duracion_min DROP NOT NULL;

-- Se toma como duración normal la más común que ya esté cargada
UPDATE servicio s SET duracion_min = COALESCE((
    SELECT sp.duracion_min FROM servicio_profesional sp
    WHERE sp.servicio_id = s.id
    GROUP BY sp.duracion_min
    ORDER BY count(*) DESC, sp.duracion_min
    LIMIT 1), 60);

-- Y se limpia lo que coincide con la norma, para que quede como ajuste real
UPDATE servicio_profesional sp SET duracion_min = NULL
FROM servicio s
WHERE sp.servicio_id = s.id AND sp.duracion_min = s.duracion_min;

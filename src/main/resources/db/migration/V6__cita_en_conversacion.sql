-- La conversación necesita recordar qué cita está moviendo la clienta
ALTER TABLE conversacion ADD COLUMN cita_id BIGINT REFERENCES cita(id) ON DELETE SET NULL;
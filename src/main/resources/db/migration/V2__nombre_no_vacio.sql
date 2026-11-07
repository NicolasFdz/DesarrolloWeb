-- Agrega la regla sin tocar V1: el esquema evoluciona con un paso nuevo.
ALTER TABLE alumnos
    ADD CONSTRAINT ck_nombre_no_vacio CHECK (LENGTH(TRIM(nombre)) > 0);
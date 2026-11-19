-- Los datos de solicitud y documentos viajan por la misma cola transaccional.
ALTER TABLE cursos_pendientes ADD COLUMN IF NOT EXISTS solicitud_web text;
ALTER TABLE cursos_pendientes ADD COLUMN IF NOT EXISTS documento_tipo varchar(12);
ALTER TABLE cursos_pendientes ADD COLUMN IF NOT EXISTS habilitar_empresa boolean NOT NULL DEFAULT false;

CREATE TABLE IF NOT EXISTS referencias_portal (
    referencia varchar(50) PRIMARY KEY
);

\connect institucion_cursos

ALTER TABLE cursos ADD COLUMN IF NOT EXISTS solicitud_web text;
ALTER TABLE empresas ADD COLUMN IF NOT EXISTS acceso_habilitado boolean NOT NULL DEFAULT false;
ALTER TABLE empresas ADD COLUMN IF NOT EXISTS clave_portal text;

CREATE TABLE IF NOT EXISTS documentos_curso (
    proceso_id varchar(128) NOT NULL REFERENCES cursos(proceso_id),
    tipo varchar(12) NOT NULL CHECK (tipo IN ('PROPUESTA', 'INFORME')),
    revision bigint NOT NULL,
    contenido bytea NOT NULL,
    PRIMARY KEY (proceso_id, tipo, revision)
);

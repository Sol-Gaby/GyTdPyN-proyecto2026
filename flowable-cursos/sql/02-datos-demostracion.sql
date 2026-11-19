\connect institucion_cursos

INSERT INTO empresas(codigo, nombre) VALUES
    ('DEMO', 'Empresa de demostración'),
    ('EMP-RC', 'Capacitación Río Cuarto')
ON CONFLICT (codigo) DO NOTHING;

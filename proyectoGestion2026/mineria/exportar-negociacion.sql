-- Ejecutar en la base del motor Flowable con psql.
-- Se exportan hitos de negociación concluidos y se excluyen las esperas no tomadas.
COPY (
    WITH hitos(id, etiqueta) AS (VALUES
        ('Inicio', 'Solicitud recibida'),
        ('Activity_1xw400l', 'Evaluar viabilidad'),
        ('Activity_0iibzwu', 'Notificar no viable'),
        ('Propuesta', 'Elaborar propuesta'),
        ('Enviar', 'Enviar propuesta'),
        ('Recordatorio', 'Enviar recordatorio'),
        ('ValidarModificaciones', 'Recibir modificaciones'),
        ('Revisar', 'Revisar propuesta'),
        ('FinSinCurso', 'Archivar solicitud'),
        ('Activity_1eg7zcl', 'Aceptar propuesta')
    ),
    casos AS (
        SELECT p.proc_inst_id_, COALESCE(v.text_, 'ACEPTADA') AS resultado
        FROM act_hi_procinst p
        JOIN act_re_procdef d ON d.id_ = p.proc_def_id_
        LEFT JOIN act_hi_varinst v ON v.proc_inst_id_ = p.proc_inst_id_
            AND v.execution_id_ = p.proc_inst_id_ AND v.name_ = 'resultado' AND v.task_id_ IS NULL
            AND v.text_ IN ('NO_VIABLE', 'RECHAZADO', 'SIN_RESPUESTA')
        WHERE d.key_ = 'Process_Curso' AND p.delete_reason_ IS NULL
            AND EXISTS (
                SELECT 1 FROM act_hi_actinst a
                WHERE a.proc_inst_id_ = p.proc_inst_id_ AND a.end_time_ IS NOT NULL
                    AND a.delete_reason_ IS NULL
                    AND (a.act_id_ = 'Activity_1eg7zcl' OR (p.end_time_ IS NOT NULL
                        AND a.act_id_ IN ('FinSinCurso', 'Event_18edb9q')))
            )
    )
    SELECT a.proc_inst_id_ AS instancia,
           row_number() OVER (PARTITION BY a.proc_inst_id_
               ORDER BY a.start_time_, a.transaction_order_ NULLS LAST, a.id_) AS orden,
           CASE WHEN a.act_id_ = 'FinSinCurso' THEN h.etiqueta || ' (' || c.resultado || ')'
                ELSE h.etiqueta END AS actividad,
           to_char(a.end_time_, 'YYYY-MM-DD"T"HH24:MI:SS.MS') AS fecha_motor
    FROM act_hi_actinst a
    JOIN casos c USING (proc_inst_id_)
    JOIN hitos h ON h.id = a.act_id_
    WHERE a.end_time_ IS NOT NULL AND a.delete_reason_ IS NULL
    ORDER BY instancia, orden
) TO STDOUT WITH (FORMAT CSV, HEADER, ENCODING 'UTF8');

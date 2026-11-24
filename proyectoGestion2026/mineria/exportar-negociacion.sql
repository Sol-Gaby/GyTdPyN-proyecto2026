-- Ejecutar en la base del motor Flowable con psql.
-- Se exportan hitos de negociación concluidos y se excluyen las esperas no tomadas.
COPY (
    WITH hitos(id, etiqueta) AS (VALUES
        ('Inicio', 'Solicitud recibida'),
        ('evaluar_viabilidad', 'Evaluar viabilidad'),
        ('notificar_inviabilidad', 'Notificar no viable'),
        ('reg_elav_propuesta', 'Elaborar propuesta'),
        ('enviar_propuesta', 'Enviar propuesta'),
        ('envio_recordatorio', 'Enviar recordatorio'),
        ('reg_observaciones', 'Recibir modificaciones'),
        ('revisar_propuesta', 'Revisar propuesta'),
        ('solicitud_cerrada', 'Archivar solicitud'),
        ('habilita_proceso_empresa', 'Aceptar propuesta')
    ),
    actividades AS (
        SELECT a.*, CASE a.act_id_
                WHEN 'Activity_1xw400l' THEN 'evaluar_viabilidad'
                WHEN 'Activity_0iibzwu' THEN 'notificar_inviabilidad'
                WHEN 'Propuesta' THEN 'reg_elav_propuesta'
                WHEN 'Enviar' THEN 'enviar_propuesta'
                WHEN 'Recordatorio' THEN 'envio_recordatorio'
                WHEN 'ValidarModificaciones' THEN 'reg_observaciones'
                WHEN 'Revisar' THEN 'revisar_propuesta'
                WHEN 'FinSinCurso' THEN 'solicitud_cerrada'
                WHEN 'Activity_1eg7zcl' THEN 'habilita_proceso_empresa'
                WHEN 'Event_18edb9q' THEN 'fin_inviable'
                ELSE a.act_id_
            END AS actividad_id
        FROM act_hi_actinst a
        WHERE a.end_time_ IS NOT NULL AND a.delete_reason_ IS NULL
    ),
    casos AS (
        SELECT p.proc_inst_id_, COALESCE(v.text_, 'ACEPTADA') AS resultado
        FROM act_hi_procinst p
        JOIN act_re_procdef d ON d.id_ = p.proc_def_id_
        LEFT JOIN act_hi_varinst v ON v.proc_inst_id_ = p.proc_inst_id_
            AND v.execution_id_ = p.proc_inst_id_ AND v.name_ = 'estadoFinal' AND v.task_id_ IS NULL
            AND v.text_ IN ('NO_VIABLE', 'RECHAZADO', 'SIN_RESPUESTA')
        WHERE d.key_ = 'Process_Curso' AND p.delete_reason_ IS NULL
            AND EXISTS (
                SELECT 1 FROM actividades a
                WHERE a.proc_inst_id_ = p.proc_inst_id_
                    AND (a.actividad_id = 'habilita_proceso_empresa' OR (p.end_time_ IS NOT NULL
                        AND a.actividad_id IN ('solicitud_cerrada', 'fin_inviable')))
            )
    )
    SELECT a.proc_inst_id_ AS instancia,
           row_number() OVER (PARTITION BY a.proc_inst_id_
               ORDER BY a.start_time_, a.transaction_order_ NULLS LAST, a.id_) AS orden,
           CASE WHEN a.actividad_id = 'solicitud_cerrada' THEN h.etiqueta || ' (' || c.resultado || ')'
                ELSE h.etiqueta END AS actividad,
           to_char(a.end_time_, 'YYYY-MM-DD"T"HH24:MI:SS.MS') AS fecha_motor
    FROM actividades a
    JOIN casos c USING (proc_inst_id_)
    JOIN hitos h ON h.id = a.actividad_id
    ORDER BY instancia, orden
) TO STDOUT WITH (FORMAT CSV, HEADER, ENCODING 'UTF8');

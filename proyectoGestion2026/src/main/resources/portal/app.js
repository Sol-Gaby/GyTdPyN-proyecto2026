const registro = document.querySelector('#registro');
const login = document.querySelector('#login');
const privado = document.querySelector('#privado');
const contenido = document.querySelector('#contenido');
const estado = document.querySelector('#acceso-estado');
const actualizar = document.querySelector('#actualizar');
let auth = null;
let rol = null;
let seguimientoSolicitudes = false;
let revisionCarga = 0;
let referencia = crypto.randomUUID();

function nodo(etiqueta, texto) {
    const elemento = document.createElement(etiqueta);
    if (texto) elemento.textContent = texto;
    return elemento;
}

async function api(ruta, metodo = 'GET', datos, credencial = auth) {
    const headers = {'X-Requested-With': 'XMLHttpRequest'};
    if (credencial) headers.Authorization = credencial;
    if (datos !== undefined) headers['Content-Type'] = 'application/json';
    const r = await fetch('api/' + ruta, {
        method: metodo,
        headers,
        cache: 'no-store',
        body: datos === undefined ? undefined : JSON.stringify(datos)
    });
    if (!r.ok) {
        const detalle = await r.json().catch(() => ({}));
        throw new Error(detalle.error || detalle.message || (r.status === 401
            ? 'Credenciales inválidas.' : 'La operación devolvió HTTP ' + r.status));
    }
    if (r.status === 204) return;
    if (r.headers.get('Content-Type')?.includes('application/pdf')) return r.blob();
    return r.json();
}

registro.addEventListener('submit', async e => {
    e.preventDefault();
    const boton = registro.querySelector('button');
    boton.disabled = true;
    const aviso = document.querySelector('#registro-estado');
    try {
        const resultado = await api('solicitudes', 'POST', {
            ...Object.fromEntries(new FormData(registro)), solicitudId: referencia
        }, null);
        aviso.textContent = 'Solicitud registrada. Instancia ' + resultado.processInstanceId
            + '. Referencia ' + resultado.referenciaSolicitud + '.'
            + (resultado.estado === 'NO_VIABLE'
                ? ' La solicitud quedó cerrada por el límite de cursos en trámite.'
                : ' Usuario para el seguimiento después de aceptar: ' + resultado.usuarioEmpresa);
        referencia = crypto.randomUUID();
        registro.reset();
    } catch (error) {
        aviso.textContent = error.message;
        return;
    } finally { boton.disabled = false; }
    if (auth && seguimientoSolicitudes) {
        try {
            await cargar();
        } catch (error) {
            estado.textContent = 'La solicitud se registró, pero no se pudo actualizar el panel. ' + error.message;
        }
    }
});

login.addEventListener('submit', async e => {
    e.preventDefault();
    revisionCarga++;
    auth = null;
    rol = null;
    seguimientoSolicitudes = false;
    privado.hidden = true;
    contenido.replaceChildren();
    const campos = new FormData(login);
    const bytes = new TextEncoder().encode(campos.get('usuario') + ':' + campos.get('clave'));
    const credencial = 'Basic ' + btoa(String.fromCharCode(...bytes));
    try {
        const usuario = await api('yo', 'GET', undefined, credencial);
        auth = credencial;
        rol = usuario.rol;
        seguimientoSolicitudes = usuario.seguimientoSolicitudes;
        login.reset();
        privado.hidden = false;
        estado.textContent = 'Sesión de ' + usuario.usuario;
        await cargar();
    } catch (error) { estado.textContent = error.message; }
});

document.querySelector('#salir').onclick = () => {
    revisionCarga++;
    auth = null;
    rol = null;
    seguimientoSolicitudes = false;
    privado.hidden = true;
    contenido.replaceChildren();
    estado.textContent = 'Sesión cerrada.';
};
actualizar.onclick = async () => {
    actualizar.disabled = true;
    estado.textContent = 'Actualizando…';
    try {
        if (await cargar()) estado.textContent = 'Lista actualizada.';
    } catch (error) {
        estado.textContent = 'No se pudo actualizar la lista. ' + error.message;
    } finally {
        actualizar.disabled = false;
    }
};

async function cargar() {
    const revision = ++revisionCarga;
    const empresa = rol === 'EMPRESA';
    let filas;
    let seguimiento;
    try {
        [filas, seguimiento] = await Promise.all([
            api(empresa ? 'empresa/cursos' : 'tareas'),
            seguimientoSolicitudes
                ? api('tareas/espera').then(propuestas => ({propuestas})).catch(error => ({error}))
                : {propuestas: []}
        ]);
    } catch (error) {
        if (revision !== revisionCarga) return false;
        throw error;
    }
    if (revision !== revisionCarga) return false;
    const listaActualizada = document.createDocumentFragment();
    if (filas.length === 0) {
        listaActualizada.append(nodo('p', empresa ? 'No hay cursos habilitados.' : 'No hay tareas disponibles.'));
    }
    for (const fila of filas) {
        const bloque = nodo('article');
        bloque.append(nodo('h3', empresa ? fila.nombre : fila.name));
        if (empresa) {
            bloque.append(nodo('p', fila.estado.replaceAll('_', ' ')));
            if (fila.actividades?.length) {
                const lista = nodo('ul');
                for (const a of fila.actividades) lista.append(nodo('li', a));
                bloque.append(lista);
            }
        } else {
            if (fila.empresaNombre) {
                bloque.append(nodo('p', 'Empresa: ' + fila.empresaNombre));
            }
            bloque.append(nodo('p', 'Instancia ' + fila.processInstanceId));
            agregarAccionDeTarea(fila, bloque);
        }
        listaActualizada.append(bloque);
    }
    if (seguimiento.error) {
        listaActualizada.append(nodo('p', 'No se pudieron consultar las propuestas en espera. ' + seguimiento.error.message));
    } else if (seguimiento.propuestas.length) {
        listaActualizada.append(nodo('h3', 'Propuestas en espera de respuesta'));
        for (const propuesta of seguimiento.propuestas) {
            const bloque = nodo('article');
            bloque.append(
                nodo('strong', propuesta.cursoNombre || 'Curso de capacitación'),
                nodo('p', 'Empresa: ' + (propuesta.empresaNombre || propuesta.empresaCodigo || 'Sin identificar')),
                nodo('p', 'Instancia ' + propuesta.processInstanceId)
            );
            listaActualizada.append(bloque);
        }
    }
    contenido.replaceChildren(listaActualizada);
    return true;
}

function agregarAccionDeTarea(tarea, bloque) {
    if (!tarea.assignee) {
        bloque.append(boton('Reclamar', () => actuar(tarea.id, 'reclamar', {})));
        return;
    }

    switch (tarea.claveActividad) {
        case 'reg_elav_propuesta':
            bloque.append(boton('Elaborar propuesta', () => abrir(tarea, bloque)));
            break;
        case 'archivar_solicitud':
            bloque.append(boton('Archivar solicitud', () => actuar(tarea.id, 'completar', {})));
            break;
        default:
            bloque.append(boton('Abrir tarea', () => abrir(tarea, bloque)));
    }
}

const campos = {
    reg_elav_propuesta: [['programa','Programa','texto'],['presupuesto','Presupuesto','number'],['fechasPosibles','Fechas posibles','text']],
    revisar_propuesta: [['programa','Programa','texto'],['presupuesto','Presupuesto','number'],['fechasPosibles','Fechas posibles','text']],
    evaluar_recursos: [['requiereAula','Aula física','checkbox'],['requierePlataforma','Aula virtual','checkbox'],['requiereEquipamiento','Equipamiento especial','checkbox']],
    definir_docente_resp: [['docenteAsignado','Docente','docentes']],
    Informe: [['informeResultados','Informe de resultados','texto']]
};

function boton(texto, accion) {
    const b = nodo('button', texto);
    b.type = 'button';
    b.onclick = async () => {
        b.disabled = true;
        try { await accion(); }
        catch (error) { estado.textContent = error.message; }
        finally { b.disabled = false; }
    };
    return b;
}

async function actuar(id, accion, valores) {
    await api('tareas/' + encodeURIComponent(id) + '/' + accion, 'POST', valores);
    estado.textContent = accion === 'reclamar' ? 'Tarea reclamada.' : 'Tarea completada.';
    await cargar();
}

async function abrir(tarea, bloque) {
    bloque.querySelector('form')?.remove();
    bloque.querySelector('.detalle')?.remove();
    const d = await api('tareas/' + encodeURIComponent(tarea.id) + '/datos');
    const clave = tarea.claveActividad;
    const detalle = nodo('div');
    detalle.className = 'detalle';
    if (clave === 'revisar_propuesta' && d.observaciones) detalle.append(nodo('p', d.observaciones));
    if (clave === 'confeccion_lista' && d.nomina) detalle.append(nodo('p', d.nomina));
    if (clave === 'envio_Informe') {
        detalle.append(boton('Descargar informe PDF', async () => {
            const archivo = await api('documentos/' + encodeURIComponent(tarea.processInstanceId) + '/INFORME');
            const url = URL.createObjectURL(archivo);
            const enlace = nodo('a');
            enlace.href = url;
            enlace.download = 'informe.pdf';
            enlace.click();
            setTimeout(() => URL.revokeObjectURL(url), 1000);
        }));
    }
    bloque.append(detalle);
    const form = nodo('form');
    for (const [id, titulo, tipo] of campos[clave] || []) {
        const label = nodo('label', titulo);
        const input = nodo(tipo === 'texto' ? 'textarea' : tipo === 'docentes' ? 'select' : 'input');
        input.name = id;
        if (tipo === 'docentes') {
            for (const docente of ['jefeDocente','docente1','docente2']) {
                const opcion = nodo('option', docente);
                opcion.value = docente;
                input.append(opcion);
            }
        } else if (input.tagName === 'INPUT') { input.type = tipo; }
        if (tipo !== 'checkbox') input.required = true;
        if (tipo === 'number') input.min = 0;
        if (d[id]) input.value = d[id];
        label.append(input);
        form.append(label);
    }
    const enviar = nodo('button', clave === 'reg_elav_propuesta' ? 'Completar propuesta' : 'Completar');
    enviar.type = 'submit';
    form.append(enviar);
    form.onsubmit = async e => {
        e.preventDefault();
        enviar.disabled = true;
        const valores = {};
        for (const [id, , tipo] of campos[clave] || []) {
            const input = form.elements[id];
            valores[id] = tipo === 'checkbox' ? input.checked : tipo === 'number' ? input.valueAsNumber : input.value;
        }
        try { await actuar(tarea.id, 'completar', valores); }
        catch (error) { estado.textContent = error.message; }
        finally { enviar.disabled = false; }
    };
    bloque.append(form);
}

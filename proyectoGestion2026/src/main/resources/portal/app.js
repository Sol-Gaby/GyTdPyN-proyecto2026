const registro = document.querySelector('#registro');
const login = document.querySelector('#login');
const privado = document.querySelector('#privado');
const contenido = document.querySelector('#contenido');
const estado = document.querySelector('#acceso-estado');
let auth = null;
let rol = null;
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
    const r = await fetch('api/' + ruta, { method: metodo, headers, body: datos === undefined ? undefined : JSON.stringify(datos) });
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
        const datos = Object.fromEntries(new FormData(registro));
        const resultado = await api('solicitudes', 'POST', {
            ...datos,
            solicitudId: referencia
        }, null);

        aviso.textContent = 'Solicitud registrada para la empresa '
            + datos.empresaCodigo.trim() + ' · ' + datos.empresaNombre.trim();
        referencia = crypto.randomUUID();
        registro.reset();
    } catch (error) {
        aviso.textContent = error.message;
    } finally { boton.disabled = false; }
});

login.addEventListener('submit', async e => {
    e.preventDefault();
    auth = null;
    rol = null;
    privado.hidden = true;
    contenido.replaceChildren();
    const campos = new FormData(login);
    const bytes = new TextEncoder().encode(campos.get('usuario') + ':' + campos.get('clave'));
    const credencial = 'Basic ' + btoa(String.fromCharCode(...bytes));
    try {
        const usuario = await api('yo', 'GET', undefined, credencial);
        auth = credencial;
        rol = usuario.rol;
        login.reset();
        privado.hidden = false;
        estado.textContent = 'Sesión de ' + usuario.usuario;
        await cargar();
    } catch (error) { estado.textContent = error.message; }
});

document.querySelector('#salir').onclick = () => {
    auth = null;
    rol = null;
    privado.hidden = true;
    contenido.replaceChildren();
    estado.textContent = 'Sesión cerrada.';
};
document.querySelector('#actualizar').onclick = () => cargar().catch(error => { estado.textContent = error.message; });

async function cargar() {
    const empresa = rol === 'EMPRESA';
    const filas = await api(empresa ? 'empresa/cursos' : 'tareas');
    contenido.replaceChildren();
    if (filas.length === 0) contenido.append(nodo('p', empresa ? 'No hay cursos habilitados.' : 'No hay tareas disponibles.'));
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
            const empresaSolicitud = [fila.empresaCodigo, fila.empresaNombre]
                .filter(Boolean)
                .join(' · ');
            bloque.append(nodo('p', empresaSolicitud
                ? 'Empresa ' + empresaSolicitud
                : 'Instancia ' + fila.processInstanceId));
        }
        contenido.append(bloque);
    }
}

const campos = {
    Propuesta: [['programa','Programa','texto'],['presupuesto','Presupuesto','number'],['fechasPosibles','Fechas posibles','text']],
    Revisar: [['programa','Programa','texto'],['presupuesto','Presupuesto','number'],['fechasPosibles','Fechas posibles','text']],
    Activity_12i2hpf: [['requiereAula','Aula física','checkbox'],['requierePlataforma','Aula virtual','checkbox'],['requiereEquipamiento','Equipamiento especial','checkbox']],
    Activity_1018bdz: [['docenteAsignado','Docente','docentes']],
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
    const clave = tarea.taskDefinitionKey;
    const detalle = nodo('div');
    detalle.className = 'detalle';
    if (clave === 'Revisar' && d.observaciones) detalle.append(nodo('p', d.observaciones));
    if (clave === 'Lista' && d.nomina) detalle.append(nodo('p', d.nomina));
    if (clave === 'EnviarInforme') {
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
    const enviar = nodo('boton_enviar', 'Completar');
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

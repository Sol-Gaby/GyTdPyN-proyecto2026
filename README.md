# Gestión de cursos de capacitación

**Integrantes**

- Ludueña Augusto (45342081)
- Adorno Gabriela Soledad (36182765)

## Objetivo del proyecto

En este trabajo implementamos en Flowable el proceso que sigue una institución cuando una empresa solicita un curso de capacitación. Desde el portal se registra la solicitud y se sigue su avance. El proceso permite elaborar la propuesta, preparar el curso y cerrar el expediente. Usamos PostgreSQL para guardar los datos de las solicitudes, las propuestas y el resultado de cada curso.

## Cómo levantar el proyecto

### 1. Preparar Docker y las contraseñas

Abrimos Docker Desktop y esperamos a que esté iniciado. Después, desde la raíz del repositorio, entramos a la carpeta de Docker:

```bat
cd flowable-cursos
```

Si ya tenemos un archivo `.env` en esa carpeta, usamos el que veníamos utilizando. Si falta, creamos un archivo llamado `.env` sin extensión adicional y agregamos estas variables. Reemplazamos los dos valores `ELEGIR_...` por nuestras propias contraseñas.

```dotenv
POSTGRES_DB=flowable
POSTGRES_USER=flowable
POSTGRES_PASSWORD=ELEGIR_CLAVE_POSTGRES
CURSOS_PERSONAL_PASSWORD=ELEGIR_CLAVE_PERSONAL
```

`POSTGRES_PASSWORD` se usa para conectar con la base. `CURSOS_PERSONAL_PASSWORD` se asigna al personal que se crea al instalar el proceso. Si ya existe un volumen de PostgreSQL, usamos la contraseña con la que se configuró originalmente.

### 2. Construir la imagen y arrancar los servicios

Desde `flowable-cursos`, ejecutamos:

```bat
docker compose config -q
docker compose up -d --build
docker compose ps -a
```

Primero validamos la configuración. Después Docker compila el proyecto, prepara la base institucional y levanta Flowable. En el listado, esperamos que `database` figure como **healthy**, `preparar-institucion` como **Exited (0)** y `flowable` como **Up**. El servicio `preparar-institucion` termina cuando completa su trabajo.

### 3. Instalar el proceso de cursos

Abrimos [Flowable UI](http://localhost:8082/flowable-ui/) y esperamos a que aparezca la pantalla de inicio de sesión. Luego ejecutamos en la misma terminal:

```bat
docker compose exec -T flowable java -cp "/app/WEB-INF/lib/*" proyecto.cursos.InstalarCursos
```

El instalador publica el proceso `Process_Curso`, la tabla de decisión y los formularios. También crea los usuarios de personal que falten. Esperamos que aparezca **Process_Curso disponible**.

### 4. Abrir el portal y hacer una prueba

Entramos al [portal de cursos](http://localhost:8082/flowable-ui/cursos/) y completamos **Solicitar una capacitación**. Después ingresamos como `jefeAdmin` con la contraseña de personal que configuramos al crear la cuenta. Para una solicitud viable, debe aparecer **Registrar y elaborar propuesta**. Abrimos la tarea, cargamos el programa, el presupuesto y las fechas posibles, y pulsamos **Completar propuesta**.

Si queremos comprobar la definición desplegada, entramos a **Flowable UI → Admin → Process Definitions** y buscamos **Organizar curso de capacitación**. En una instalación nueva, la cuenta de demostración de Flowable UI es `admin` con contraseña `test`.

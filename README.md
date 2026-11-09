# RutaSmart 🧭

**Asistente inteligente para seleccionar la mejor alternativa de transporte hacia un destino.**
Aplicación Android en Kotlin con Jetpack Compose, MVVM por capas, Room, Firebase y un asistente con IA (Google Gemini).

**Autora:** Sarah Isabella Peña Cortes · Universidad de San Buenaventura, Bogotá · Optativa II: Desarrollo de Aplicaciones para Dispositivos Móviles · Docente: Ing. Jairo Armando Salcedo Aranda

---

## ¿Qué hace?

1. El usuario escribe **origen y destino** (con sugerencias de lugares de Bogotá).
2. Define sus **prioridades** de 0 a 5: tiempo, costo, comodidad, transbordos e impacto ambiental, los **medios** que quiere usar y si viaja en **hora pico**.
3. La app genera las **alternativas**: caminata, bicicleta, taxi, solo TransMilenio, solo SITP y TransMilenio + SITP. Las de transporte público se calculan con el algoritmo de Dijkstra sobre la red de paradas.
4. Un **motor de puntaje** propio calcula un puntaje de 0 a 100 para cada alternativa y las ordena. La primera es la **recomendada**.
5. El **detalle** muestra el mapa esquemático de la ruta, el paso a paso y un botón para abrirla en Google Maps.
6. El **asistente Rutina** (botón flotante 🤖, disponible en todas las pantallas) explica en lenguaje natural por qué se recomienda una ruta y responde preguntas como «¿cuál es la más económica?» o «¿y en hora pico?».
7. Cada búsqueda queda en el **historial**: en Room (local) y, con sesión iniciada, también en Cloud Firestore.
8. **Administración (🛠, solo administradores):** estadísticas de uso de todos los usuarios, **suspender líneas** cerradas (dejan de usarse en todas las búsquedas) y **avisos** que aparecen en el inicio de todos.

## Arquitectura (MVVM + capas inspiradas en Clean Architecture)

```
ui/            Presentación: pantallas Compose, navegación (Navigation Compose) y ViewModels con StateFlow
domain/        Dominio (Kotlin puro, probado con JUnit)
  model/         Parada, Ruta, Tramo, MedioDeTransporte (Bus, Metro, Taxi, Bicicleta, Caminata),
                 Alternativa, CriteriosUsuario, ConsultaHistorial, excepciones propias
  motor/         RedTransporte (grafo con HashMap), Planificador (Dijkstra), MotorPuntaje, Bitacora (log)
  casosdeuso/    BuscarLugarUseCase, ObtenerAlternativasUseCase, RecomendarRutaUseCase, ValidadorCuenta
  admin/         EstadoServicio, AdminValidador, Estadisticas (panel de administración)
  ia/            Núcleo del agente (validación, límites, errores) y asistente local + prompt de Gemini
data/          Datos (patrón Repository)
  local/         Room: tablas consultas y preferencias
  remoto/        FirebaseRemoto (Auth + Firestore) y GeminiClient (HTTPS)
  red/           Red de transporte de ejemplo de Bogotá (TransMilenio y SITP)
  repositorio/   HistorialRepositorio, PreferenciasRepositorio, AsistenteRepositorio, ServicioRepositorio
```

## Cómo ejecutar

1. Abrir la carpeta en **Android Studio** (Koala o superior) y esperar la sincronización de Gradle.
2. ▶ **Run** en un emulador o celular con Android 8.0 o superior.

Sin configurar nada, la app funciona en **modo invitado**: todo se guarda en el teléfono y el asistente responde en modo local.

### Firebase (cuentas, historial en la nube y administración)

`app/google-services.json` ya está incluido (proyecto `rutasmart-10006`). En la consola de Firebase falta:

1. **Authentication** → activar **Correo electrónico/contraseña**.
2. **Firestore Database** → crear la base de datos.
3. **Firestore → Reglas** → pegar `firebase/firestore.rules` y **Publicar**.

### Administrador

Igual que en Mercadito: registrarse una vez en la app con **`admin@rutasmart.com`** y entrar con esa cuenta. Aparece el botón **🛠** (Administración). No hay que configurar nada más en Firebase; las reglas verifican ese mismo correo.

### Activar la IA (opcional)

En `local.properties` (raíz del proyecto, no se sube a Git) agregar:

```
GEMINI_API_KEY=tu_clave
```

La clave se crea gratis en https://aistudio.google.com/apikey. Sin clave, el asistente responde con el motor local.

## Pruebas

```
./gradlew testDebugUnitTest
```

Son 80 pruebas unitarias (JUnit) del dominio: modelo POO, grafo, planificador, puntaje, casos de uso, asistente, sincronización, validaciones y administración (líneas suspendidas, avisos, estadísticas).

## Documentación

Toda la documentación está en la carpeta [`Documentacion/`](Documentacion/README.md): documentos IEEE, matrices, manuales, casos de uso y de prueba, mockups, seguridad, agente de IA, Firebase y modelos de datos.

## Notas

- La red de transporte y las tarifas son **datos de ejemplo aproximados** de Bogotá. Se pueden reemplazar por datos oficiales (GTFS) sin cambiar el motor.
- Proyección multiplataforma: el paquete `domain/` no depende de Android y puede moverse a un módulo compartido de **Kotlin Multiplatform**.

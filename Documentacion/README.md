# Documentación de RutaSmart

**Autora:** Sarah Isabella Peña Cortes · Universidad de San Buenaventura, Bogotá · Optativa II · Docente: Ing. Jairo Armando Salcedo Aranda

Cada documento está en Word (`.docx`, editable) y en PDF.

```
Documentacion/
├── 01_Documentos_IEEE/
│   ├── Propuesta_RutaSmart_IEEE.pdf propuesta inicial del proyecto (diseño en formato IEEE)
│   ├── SRS_Requisitos               requisitos (IEEE 830 / ISO 29148): problema, alcance, RF, RNF, reglas de negocio
│   ├── Arquitectura_Diseno          arquitectura MVVM por capas, POO, estructuras y ciclos, algoritmos (Dijkstra y puntaje),
│   │                                persistencia, excepciones, seguridad contra nulos, depuración, Kotlin Multiplatform, IA (IEEE 1016)
│   ├── Plan_Pruebas                 estrategia en 3 niveles + IA + seguridad (IEEE 829)
│   ├── Informe_Pruebas              80 pruebas unitarias aprobadas, defectos corregidos, pruebas en emulador por registrar
│   └── Articulo_IEEE_RutaSmart      artículo final en formato IEEE a dos columnas: diseño, implementación, resultados y conclusiones
├── 02_Matrices/
│   ├── Historias_Usuario.xlsx       15 historias en 3 sprints (puntos con fórmulas)
│   ├── Trazabilidad_Requisitos.xlsx RF → HU → CU → CP → código
│   ├── Casos_Prueba.xlsx            45 casos con resultado y totales automáticos
│   ├── Matriz_Riesgos.xlsx          12 riesgos con exposición calculada
│   ├── Matriz_Maestra.xlsx          índice de entregables e indicadores
│   ├── Cumplimiento_Guia.xlsx       autoevaluación contra el checklist de Optativa II (calcula la nota)
│   ├── Auditoria_Archivos.xlsx      cada archivo de código: capa, líneas y responsabilidad
│   ├── Diccionario_Datos.xlsx       campos de Room, Firestore, Auth y la red en memoria (tipo, restricciones, descripción)
│   ├── Matriz_Seguridad.xlsx        controles MASVS, hallazgos, pruebas de autorización (AZ) y análisis estático
│   ├── Matriz_Agente_IA.xlsx        pruebas automáticas, escenarios en emulador y ficha del agente
│   └── Verificacion_RNF.xlsx        cómo se verificó cada requisito no funcional y su evidencia
├── 03_Manuales/                     Manual_Usuario · Manual_Tecnico · Manual_Instalacion
├── 04_Casos_Uso_y_Prueba/           diagrama UML, 16 casos de uso (incluye administración), 45 casos de prueba, matriz CU × CP
├── 05_Mockups/                      7 pantallas + 3 de administración en alta fidelidad + documento de diseño y accesibilidad
├── 06_Seguridad/                    OWASP MASVS, análisis estático (mobsfscan), secretos, hallazgos, re-pruebas, Kali/BadStore
├── 07_Agente_IA/                    asistente «Rutina»: propósito, integración con Gemini, controles, clave y escenarios
├── Documentacion_Firebase/          modelo Firestore, CRUD, sincronización, rol de administrador y reglas
└── Modelos_Datos/                   diagramas de arquitectura, clases, ER, secuencia, navegación, Dijkstra y agente (+ fuentes .mmd)
```

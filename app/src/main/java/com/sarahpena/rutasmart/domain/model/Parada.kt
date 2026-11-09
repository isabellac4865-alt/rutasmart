package com.sarahpena.rutasmart.domain.model

/**
 * Punto geográfico donde se puede subir, bajar o cambiar de medio de transporte.
 * Encapsulamiento: todos los atributos son de solo lectura (val).
 */
data class Parada(
    val id: String,
    val nombre: String,
    val latitud: Double,
    val longitud: Double
) {
    init {
        require(id.isNotBlank()) { "La parada debe tener un identificador." }
        require(latitud in -90.0..90.0 && longitud in -180.0..180.0) { "Coordenadas fuera de rango en $nombre." }
    }
}

/** Lugar que el usuario puede escoger como origen o destino (sitios conocidos de la ciudad). */
data class Lugar(
    val nombre: String,
    val latitud: Double,
    val longitud: Double,
    val zona: String
) {
    /** Un lugar se convierte en parada para poder incluirlo en una ruta (inicio o fin del trayecto). */
    fun comoParada(): Parada = Parada("lugar:$nombre", nombre, latitud, longitud)
}

/** Línea de transporte público (troncal de TransMilenio o ruta del SITP). */
data class Linea(
    val id: String,
    val nombre: String,
    val tipo: TipoMedio
)

/** Arista del grafo: desde una parada se puede ir a [destinoId] usando la [linea]. */
data class Conexion(
    val destinoId: String,
    val linea: Linea
)

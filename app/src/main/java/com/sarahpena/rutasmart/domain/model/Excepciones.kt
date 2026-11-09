package com.sarahpena.rutasmart.domain.model

/**
 * Excepciones propias de RutaSmart. Cada una trae un mensaje comprensible para el usuario,
 * de modo que la interfaz nunca muestra el error técnico.
 */
sealed class RutaSmartException(val mensajeUsuario: String, causa: Throwable? = null) : Exception(mensajeUsuario, causa)

/** No existe ninguna combinación de paradas y medios entre el origen y el destino. */
class RutaNoEncontradaException(mensaje: String = "No encontramos ninguna ruta entre esos puntos con los medios seleccionados. Prueba permitiendo más medios de transporte.") :
    RutaSmartException(mensaje)

/** El origen o destino no se reconoce (no se puede ubicar) o es inválido. */
class UbicacionInvalidaException(mensaje: String) : RutaSmartException(mensaje)

/** Falla de red o del servicio externo (Firebase, asistente de IA). */
class ServicioNoDisponibleException(
    mensaje: String = "No hay conexión con el servicio. Verifica tu internet e inténtalo de nuevo.",
    causa: Throwable? = null
) : RutaSmartException(mensaje, causa)

/** El usuario no definió ningún criterio de comparación o ningún medio permitido. */
class CriteriosIncompletosException(mensaje: String) : RutaSmartException(mensaje)

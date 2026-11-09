package com.sarahpena.rutasmart.domain.motor

/**
 * Registro interno (log) para depuración. En Android la salida se conecta a Logcat
 * (ver RutaSmartApp); en las pruebas se queda en memoria.
 */
object Bitacora {
    private const val MAXIMO = 200
    private val entradas = ArrayDeque<String>()

    /** Destino adicional de cada mensaje (por defecto, ninguno). */
    @Volatile var salida: (nivel: String, etiqueta: String, mensaje: String) -> Unit = { _, _, _ -> }

    @Synchronized
    fun info(etiqueta: String, mensaje: String) = registrar("I", etiqueta, mensaje)

    @Synchronized
    fun error(etiqueta: String, mensaje: String, e: Throwable? = null) =
        registrar("E", etiqueta, if (e == null) mensaje else "$mensaje · ${e::class.simpleName}: ${e.message}")

    private fun registrar(nivel: String, etiqueta: String, mensaje: String) {
        if (entradas.size >= MAXIMO) entradas.removeFirst()
        entradas.addLast("$nivel/$etiqueta: $mensaje")
        runCatching { salida(nivel, etiqueta, mensaje) }
    }

    @Synchronized
    fun ultimas(n: Int = 20): List<String> = entradas.toList().takeLast(n)

    @Synchronized
    fun limpiar() = entradas.clear()
}

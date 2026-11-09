package com.sarahpena.rutasmart.domain.admin

import com.sarahpena.rutasmart.domain.model.Linea

/**
 * Estado del servicio que publica el administrador y que leen todos los usuarios.
 * Se guarda en Firestore en el documento config/servicio.
 *
 * @property lineasSuspendidas ids de líneas que el planificador NO debe usar (cierres, obras, manifestaciones).
 * @property aviso mensaje corto que aparece en la pantalla de inicio de todos los usuarios ("" = sin aviso).
 * @property nivelAviso importancia del aviso (cambia el color de la tarjeta).
 */
data class EstadoServicio(
    val lineasSuspendidas: Set<String> = emptySet(),
    val aviso: String = "",
    val nivelAviso: NivelAviso = NivelAviso.INFO,
    val actualizadoMillis: Long = 0L,
    val actualizadoPor: String = ""
) {
    val hayAviso: Boolean get() = aviso.isNotBlank()
    val hayNovedades: Boolean get() = hayAviso || lineasSuspendidas.isNotEmpty()

    companion object {
        const val MAX_AVISO = 200
        const val MAX_SUSPENDIDAS = 20
        val NORMAL = EstadoServicio()
    }
}

enum class NivelAviso(val etiqueta: String, val emoji: String) {
    INFO("Información", "ℹ️"),
    ALERTA("Alerta", "⚠️"),
    URGENTE("Urgente", "🚨");

    companion object {
        fun desde(nombre: String?): NivelAviso = entries.firstOrNull { it.name == nombre } ?: INFO
    }
}

/**
 * Cuentas de administrador. Igual que en Mercadito: quien inicia sesión con uno de estos correos
 * entra como administrador. Las reglas de Firestore verifican el mismo correo, así que la seguridad
 * no depende solo de la app. (También se acepta un documento admins/{uid} creado en la consola.)
 */
object Administradores {
    val CORREOS: Set<String> = setOf("admin@rutasmart.com")

    fun esCorreoAdmin(correo: String?): Boolean {
        val limpio = correo?.trim()?.lowercase() ?: return false
        return CORREOS.any { it.lowercase() == limpio }
    }
}

/** Reglas que debe cumplir lo que publica el administrador (las mismas que exige firestore.rules). */
object AdminValidador {

    /** Quita espacios sobrantes y caracteres de control; no cambia el texto visible. */
    fun limpiarAviso(texto: String): String =
        texto.replace(Regex("[\\p{Cntrl}&&[^\\n]]"), "").replace(Regex("[ \\t]+"), " ").trim()

    /** Devuelve el mensaje de error o null si el aviso es válido. Un aviso vacío es válido (= quitar aviso). */
    fun aviso(texto: String): String? {
        val t = limpiarAviso(texto)
        return when {
            t.isEmpty() -> null
            t.length < 10 -> "El aviso es muy corto. Explica qué pasa (mínimo 10 caracteres)."
            t.length > EstadoServicio.MAX_AVISO -> "El aviso no puede superar ${EstadoServicio.MAX_AVISO} caracteres."
            t.contains("http://", true) || t.contains("https://", true) -> "Por seguridad, el aviso no puede incluir enlaces."
            else -> null
        }
    }

    /** Valida el estado completo contra las líneas que existen en la red. */
    fun estado(e: EstadoServicio, lineasDeLaRed: List<Linea>): String? {
        val ids = lineasDeLaRed.map { it.id }.toSet()
        val desconocidas = e.lineasSuspendidas - ids
        return when {
            desconocidas.isNotEmpty() -> "Línea desconocida: ${desconocidas.joinToString()}."
            e.lineasSuspendidas.size > EstadoServicio.MAX_SUSPENDIDAS -> "Demasiadas líneas suspendidas."
            e.lineasSuspendidas.size == ids.size && ids.isNotEmpty() ->
                "No puedes suspender todas las líneas: los usuarios se quedarían sin transporte público."
            else -> aviso(e.aviso)
        }
    }
}

/** Una consulta de cualquier usuario, tal como la lee el administrador (solo datos de uso, sin correos). */
data class RegistroUso(
    val uid: String,
    val origen: String,
    val destino: String,
    val recomendada: String,
    val tiempoMinutos: Int,
    val costo: Int,
    val puntaje: Double,
    val fechaMillis: Long
)

/** Resumen que se muestra en el panel del administrador. */
data class ResumenUso(
    val totalConsultas: Int,
    val usuariosActivos: Int,
    val consultasUltimos7Dias: Int,
    val rutasMasBuscadas: List<Pair<String, Int>>,
    val medioMasRecomendado: List<Pair<String, Int>>,
    val destinosPopulares: List<Pair<String, Int>>,
    val tiempoPromedio: Int,
    val costoPromedio: Int,
    val puntajePromedio: Double
) {
    val vacio: Boolean get() = totalConsultas == 0

    companion object {
        val VACIO = ResumenUso(0, 0, 0, emptyList(), emptyList(), emptyList(), 0, 0, 0.0)
    }
}

/** Calcula las estadísticas del panel a partir de las consultas (HashMap para contar frecuencias). */
object Estadisticas {
    private const val SIETE_DIAS = 7L * 24 * 60 * 60 * 1000

    fun calcular(registros: List<RegistroUso>, ahora: Long, top: Int = 5): ResumenUso {
        if (registros.isEmpty()) return ResumenUso.VACIO
        fun contar(clave: (RegistroUso) -> String): List<Pair<String, Int>> {
            val conteo = HashMap<String, Int>()
            for (r in registros) conteo[clave(r)] = (conteo[clave(r)] ?: 0) + 1
            // Mayor frecuencia primero; si empatan, orden alfabético (resultado estable).
            return conteo.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .take(top).map { it.key to it.value }
        }
        return ResumenUso(
            totalConsultas = registros.size,
            usuariosActivos = registros.map { it.uid }.toSet().size,
            consultasUltimos7Dias = registros.count { ahora - it.fechaMillis in 0..SIETE_DIAS },
            rutasMasBuscadas = contar { "${it.origen} → ${it.destino}" },
            medioMasRecomendado = contar { it.recomendada },
            destinosPopulares = contar { it.destino },
            tiempoPromedio = Math.round(registros.map { it.tiempoMinutos }.average()).toInt(),
            costoPromedio = Math.round(registros.map { it.costo }.average()).toInt(),
            puntajePromedio = Math.round(registros.map { it.puntaje }.average() * 10) / 10.0
        )
    }
}

package com.sarahpena.rutasmart.domain.casosdeuso

import com.sarahpena.rutasmart.domain.model.AlternativaPuntuada
import com.sarahpena.rutasmart.domain.model.CriteriosUsuario
import com.sarahpena.rutasmart.domain.model.Lugar
import com.sarahpena.rutasmart.domain.model.UbicacionInvalidaException
import com.sarahpena.rutasmart.domain.motor.Bitacora
import com.sarahpena.rutasmart.domain.motor.MotorPuntaje
import com.sarahpena.rutasmart.domain.motor.Planificador
import com.sarahpena.rutasmart.domain.motor.RedTransporte
import java.text.Normalizer

/** Convierte el texto que escribe el usuario en un [Lugar] conocido (geocodificación local). */
class BuscarLugarUseCase(private val catalogo: List<Lugar>) {

    /** Devuelve hasta [max] sugerencias que contienen el texto (sin importar tildes ni mayúsculas). */
    fun sugerencias(texto: String, max: Int = 6): List<Lugar> {
        val q = normalizar(texto)
        if (q.isBlank()) return emptyList()
        return catalogo.filter { normalizar(it.nombre).contains(q) || normalizar(it.zona).contains(q) }.take(max)
    }

    /** Resuelve el texto a un lugar o lanza [UbicacionInvalidaException]. */
    operator fun invoke(texto: String?, campo: String): Lugar {
        val limpio = texto?.trim().orEmpty()
        if (limpio.isEmpty()) throw UbicacionInvalidaException("Escribe el $campo.")
        val q = normalizar(limpio)
        return catalogo.firstOrNull { normalizar(it.nombre) == q }
            ?: catalogo.singleOrNull { normalizar(it.nombre).contains(q) }
            ?: throw UbicacionInvalidaException("No reconocemos «$limpio» como $campo. Elige un lugar de la lista de sugerencias.")
    }

    companion object {
        fun normalizar(s: String): String =
            Normalizer.normalize(s.lowercase().trim(), Normalizer.Form.NFD).replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }
}

/** Genera y ordena las alternativas de transporte para un trayecto. */
class ObtenerAlternativasUseCase(red: RedTransporte) {
    private val planificador = Planificador(red)

    /** Líneas suspendidas por el administrador (se actualiza al leer config/servicio de Firestore). */
    @Volatile var lineasSuspendidas: Set<String> = emptySet()

    operator fun invoke(origen: Lugar?, destino: Lugar?, criterios: CriteriosUsuario): List<AlternativaPuntuada> {
        // Validación temprana de nulos en el punto de entrada crítico.
        val o = requireNotNull(origen) { "Falta el origen" }
        val d = requireNotNull(destino) { "Falta el destino" }
        Bitacora.info("Rutas", "Buscando ${o.nombre} → ${d.nombre} (${criterios.resumen()})")
        val alternativas = planificador.generar(o, d, criterios, lineasSuspendidas)
        val ranking = MotorPuntaje.clasificar(alternativas, criterios)
        Bitacora.info("Rutas", "${ranking.size} alternativas; recomendada: ${ranking.first().alternativa.titulo} (${ranking.first().puntaje})")
        return ranking
    }
}

/** Explica, sin IA, por qué una alternativa es la recomendada (también es el respaldo del asistente). */
class RecomendarRutaUseCase {

    fun explicar(ranking: List<AlternativaPuntuada>, criterios: CriteriosUsuario): String {
        val mejor = ranking.firstOrNull() ?: return "Aún no hay alternativas. Busca una ruta primero."
        val a = mejor.alternativa
        val sb = StringBuilder()
        sb.append("Te recomiendo «${a.titulo}» con ${formatearPuntaje(mejor.puntaje)}/100 puntos: ")
        sb.append("${a.tiempoTotalMinutos} min, ${formatearPesos(a.costoTotal)}, ${a.transbordos} transbordo(s). ")
        sb.append("Tus criterios: ${criterios.resumen()}.")
        val segunda = ranking.getOrNull(1)?.alternativa
        if (segunda != null) {
            val dt = segunda.tiempoTotalMinutos - a.tiempoTotalMinutos
            val dc = segunda.costoTotal - a.costoTotal
            sb.append("\nFrente a «${segunda.titulo}»: ")
            sb.append(
                listOfNotNull(
                    if (dt > 0) "llegas $dt min antes" else if (dt < 0) "tardas ${-dt} min más" else null,
                    if (dc > 0) "ahorras ${formatearPesos(dc)}" else if (dc < 0) "pagas ${formatearPesos(-dc)} más" else null
                ).ifEmpty { listOf("rinde parecido, pero encaja mejor con tus prioridades") }.joinToString(" y ")
            )
            sb.append(".")
        }
        return sb.toString()
    }

    companion object {
        fun formatearPesos(valor: Int): String {
            val s = valor.toString().reversed().chunked(3).joinToString(".").reversed()
            return "$$s"
        }
        fun formatearPuntaje(p: Double): String = if (p % 1.0 == 0.0) p.toInt().toString() else String.format(java.util.Locale.US, "%.1f", p)
    }
}

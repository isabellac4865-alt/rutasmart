package com.sarahpena.rutasmart.domain.ia

/**
 * Núcleo del agente de IA (lógica pura, sin Android, para poder probarla con JUnit).
 *
 * El agente es HÍBRIDO:
 *  1. El chatbot local (reglas) calcula los datos exactos de la app.
 *  2. Gemini redacta la respuesta en lenguaje natural usando esos datos como contexto.
 *  3. Si la IA no está configurada o falla, se responde con el chatbot local (la app nunca se queda sin respuesta).
 */
object AiConfig {
    const val PROVIDER = "Google Gemini"
    const val MODEL = "gemini-3.5-flash-lite"
    private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent"

    const val MAX_INPUT_CHARS = 500
    const val MAX_OUTPUT_CHARS = 1200
    const val HISTORY_TURNS = 6
    const val TIMEOUT_MS = 20_000
    const val MAX_REQUESTS_PER_MINUTE = 10

    fun endpoint(model: String = MODEL): String = ENDPOINT.format(model)
}

/** Origen de cada mensaje del chat (se muestra al usuario para que sepa si habla con la IA). */
enum class MsgSource { USER, AI, LOCAL }

/** Un turno de conversación en el formato de Gemini ("user" / "model"). */
data class Turn(val role: String, val text: String) {
    companion object {
        const val USER = "user"
        const val MODEL = "model"
    }
}

sealed class InputCheck {
    data class Ok(val text: String) : InputCheck()
    data class Rejected(val reason: String) : InputCheck()
}

/** Valida y limpia lo que escribe el usuario ANTES de enviarlo a la IA. */
object AiInputGuard {
    private val INJECTION = listOf(
        Regex("ignora\\s+(todas\\s+)?(las\\s+|tus\\s+)?(instrucciones|reglas)"),
        Regex("olvida\\s+(todas\\s+)?(las\\s+|tus\\s+)?(instrucciones|reglas)"),
        Regex("ignore\\s+(all\\s+)?(previous\\s+|the\\s+)?instructions"),
        Regex("system\\s*prompt"),
        Regex("(muestra|revela|dime)\\s+(tu|tus|la)\\s+(prompt|instrucciones|api\\s*key|clave)")
    )

    fun check(raw: String): InputCheck {
        // Quita caracteres de control (salvo saltos de línea) y espacios repetidos.
        val text = raw.filter { it == '\n' || !it.isISOControl() }
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
        if (text.isEmpty()) return InputCheck.Rejected("Escribe un mensaje para poder ayudarte.")
        if (text.length > AiConfig.MAX_INPUT_CHARS)
            return InputCheck.Rejected("Tu mensaje es muy largo (máximo ${AiConfig.MAX_INPUT_CHARS} caracteres). Resúmelo un poco, por favor.")
        val n = TextNorm.normalize(text)
        if (INJECTION.any { it.containsMatchIn(n) })
            return InputCheck.Rejected("No puedo cambiar mis instrucciones ni mostrar información interna. Pregúntame algo sobre la app y con gusto te ayudo.")
        return InputCheck.Ok(text)
    }
}

/** Revisa la respuesta de la IA ANTES de mostrarla. Devuelve null si no es utilizable. */
object AiResponseGuard {
    fun clean(text: String?): String? {
        if (text == null) return null
        var t = text
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")      // negritas markdown
            .replace(Regex("(?m)^#{1,6}\\s*"), "")          // títulos markdown
            .replace(Regex("(?m)^\\s*[*-]\\s+"), "• ")       // viñetas
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
        if (t.isEmpty()) return null
        if (t.length > AiConfig.MAX_OUTPUT_CHARS) {
            val cut = t.substring(0, AiConfig.MAX_OUTPUT_CHARS)
            val end = cut.lastIndexOfAny(charArrayOf('.', '!', '?', '\n'))
            t = (if (end > AiConfig.MAX_OUTPUT_CHARS / 2) cut.substring(0, end + 1) else cut).trim() + " …"
        }
        return t
    }
}

/** Traduce los errores del servicio de IA a mensajes claros para el usuario. */
object AiErrorMapper {
    const val NOT_CONFIGURED = "La IA no está configurada (falta la clave GEMINI_API_KEY)."
    const val NO_INTERNET = "Sin conexión a internet."
    const val TIMEOUT = "La IA tardó demasiado en responder."
    const val BLOCKED = "La IA no respondió por sus filtros de seguridad."
    const val BAD_RESPONSE = "La IA devolvió una respuesta inválida."
    const val RATE_LIMITED_LOCAL = "Estás enviando mensajes muy rápido."

    fun fromHttp(code: Int): String = when (code) {
        400 -> "Solicitud inválida para la IA (400)."
        401, 403 -> "La clave de la IA no es válida o no tiene permisos ($code)."
        404 -> "El modelo de IA no existe o cambió de nombre (404)."
        408 -> TIMEOUT
        429 -> "Se alcanzó el límite de uso de la IA (429). Intenta en un momento."
        in 500..599 -> "El servicio de IA no está disponible ($code)."
        else -> "Error inesperado de la IA ($code)."
    }

    /** Texto final cuando se responde en modo local porque la IA falló. */
    fun fallbackNote(reason: String): String = "ℹ️ $reason Te respondí en modo local."
}

/** Limita las solicitudes a la IA para evitar abuso y consumo excesivo de la clave. */
class AiRateLimiter(
    private val maxRequests: Int = AiConfig.MAX_REQUESTS_PER_MINUTE,
    private val windowMs: Long = 60_000
) {
    private val times = ArrayDeque<Long>()

    fun tryAcquire(now: Long = System.currentTimeMillis()): Boolean {
        while (times.isNotEmpty() && now - times.first() >= windowMs) times.removeFirst()
        if (times.size >= maxRequests) return false
        times.addLast(now)
        return true
    }
}

/** Utilidades para armar el historial que se envía a la IA. */
object AiHistory {
    /** Últimos [maxTurns] turnos; Gemini exige que el primero sea del usuario. */
    fun trim(turns: List<Turn>, maxTurns: Int = AiConfig.HISTORY_TURNS): List<Turn> {
        val last = turns.filter { it.text.isNotBlank() }.takeLast(maxTurns)
        return last.dropWhile { it.role != Turn.USER }
    }
}

object TextNorm {
    fun normalize(text: String): String =
        java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
}

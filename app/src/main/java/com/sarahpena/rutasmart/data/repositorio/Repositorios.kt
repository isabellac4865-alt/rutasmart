package com.sarahpena.rutasmart.data.repositorio

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.sarahpena.rutasmart.data.local.ConsultaDao
import com.sarahpena.rutasmart.data.local.ConsultaEntity
import com.sarahpena.rutasmart.data.local.PreferenciasDao
import com.sarahpena.rutasmart.data.local.PreferenciasEntity
import com.sarahpena.rutasmart.data.remoto.FirebaseRemoto
import com.sarahpena.rutasmart.data.remoto.GeminiClient
import com.sarahpena.rutasmart.data.remoto.Usuario
import com.sarahpena.rutasmart.domain.admin.AdminValidador
import com.sarahpena.rutasmart.domain.admin.EstadoServicio
import com.sarahpena.rutasmart.domain.admin.Estadisticas
import com.sarahpena.rutasmart.domain.admin.ResumenUso
import com.sarahpena.rutasmart.domain.casosdeuso.ObtenerAlternativasUseCase
import com.sarahpena.rutasmart.domain.ia.AiErrorMapper
import com.sarahpena.rutasmart.domain.ia.AiHistory
import com.sarahpena.rutasmart.domain.ia.AiRateLimiter
import com.sarahpena.rutasmart.domain.ia.AiResponseGuard
import com.sarahpena.rutasmart.domain.ia.AsistenteLocal
import com.sarahpena.rutasmart.domain.ia.ContextoRuta
import com.sarahpena.rutasmart.domain.ia.MsgSource
import com.sarahpena.rutasmart.domain.ia.RutaAiPrompt
import com.sarahpena.rutasmart.domain.ia.Turn
import com.sarahpena.rutasmart.domain.model.ConsultaHistorial
import com.sarahpena.rutasmart.domain.model.CriteriosUsuario
import com.sarahpena.rutasmart.domain.model.Linea
import com.sarahpena.rutasmart.domain.model.RutaSmartException
import com.sarahpena.rutasmart.domain.model.ServicioNoDisponibleException
import com.sarahpena.rutasmart.domain.model.SincronizadorHistorial
import com.sarahpena.rutasmart.domain.motor.Bitacora
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Comprueba si el teléfono tiene internet. */
class EstadoRed(context: Context) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun hayRed(): Boolean {
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

/** Patrón Repository: la app pide datos sin saber si vienen de Room o de Firestore. */
class HistorialRepositorio(
    private val dao: ConsultaDao,
    private val remoto: FirebaseRemoto,
    private val red: EstadoRed
) {
    fun observar(usuario: Usuario): Flow<List<ConsultaHistorial>> =
        dao.observar(usuario.uid).map { lista -> lista.map { it.aDominio() } }

    /** Guarda primero en Room (funciona sin internet) y luego intenta subir a la nube. */
    suspend fun guardar(usuario: Usuario, consulta: ConsultaHistorial) {
        dao.guardar(ConsultaEntity.desde(consulta, usuario.uid))
        sincronizar(usuario)
    }

    /** Sube lo pendiente con el ciclo while del [SincronizadorHistorial]. */
    suspend fun sincronizar(usuario: Usuario): SincronizadorHistorial.Resultado? {
        if (usuario.esInvitado || !remoto.disponible) return null
        return withContext(Dispatchers.IO) {
            val pendientes = dao.pendientes(usuario.uid).map { it.aDominio() }
            val marcadas = mutableListOf<String>()
            val r = SincronizadorHistorial.sincronizar(
                pendientes = pendientes,
                hayRed = { red.hayRed() },
                subir = { remoto.subirConsulta(usuario.uid, it) },
                marcarSincronizada = { marcadas += it }
            )
            marcadas.forEach { dao.marcarSincronizada(it) }
            Bitacora.info("Sync", "Subidas ${r.subidas}, pendientes ${r.pendientes}")
            r
        }
    }

    /** Al iniciar sesión trae el historial de la nube (por si se usó otro teléfono). */
    suspend fun descargar(usuario: Usuario) {
        if (usuario.esInvitado || !remoto.disponible || !red.hayRed()) return
        withContext(Dispatchers.IO) {
            try {
                val nube = remoto.descargarConsultas(usuario.uid)
                dao.guardarTodas(nube.map { ConsultaEntity.desde(it, usuario.uid) })
            } catch (e: Exception) {
                Bitacora.error("Sync", "No se pudo descargar el historial", e)
            }
        }
    }

    suspend fun eliminar(usuario: Usuario, id: String) {
        dao.eliminar(id)
        if (!usuario.esInvitado && remoto.disponible && red.hayRed()) withContext(Dispatchers.IO) {
            runCatching { remoto.eliminarConsulta(usuario.uid, id) }.onFailure { Bitacora.error("Sync", "No se pudo borrar $id en la nube", it) }
        }
    }

    suspend fun eliminarTodas(usuario: Usuario, ids: List<String>) {
        dao.eliminarTodas(usuario.uid)
        if (!usuario.esInvitado && remoto.disponible && red.hayRed()) withContext(Dispatchers.IO) {
            for (id in ids) runCatching { remoto.eliminarConsulta(usuario.uid, id) }
        }
    }
}

class PreferenciasRepositorio(
    private val dao: PreferenciasDao,
    private val remoto: FirebaseRemoto,
    private val red: EstadoRed
) {
    fun observar(usuario: Usuario): Flow<CriteriosUsuario> =
        dao.observar(usuario.uid).map { it?.aDominio() ?: CriteriosUsuario() }

    suspend fun guardar(usuario: Usuario, criterios: CriteriosUsuario) {
        dao.guardar(PreferenciasEntity.desde(criterios, usuario.uid))
        if (!usuario.esInvitado && remoto.disponible && red.hayRed()) withContext(Dispatchers.IO) {
            runCatching { remoto.guardarPreferencias(usuario.uid, criterios) }
                .onFailure { Bitacora.error("Prefs", "No se pudieron subir las preferencias", it) }
        }
    }

    suspend fun descargar(usuario: Usuario) {
        if (usuario.esInvitado || !remoto.disponible || !red.hayRed()) return
        withContext(Dispatchers.IO) {
            runCatching { remoto.descargarPreferencias(usuario.uid) }
                .onSuccess { c -> if (c != null) dao.guardar(PreferenciasEntity.desde(c, usuario.uid)) }
                .onFailure { Bitacora.error("Prefs", "No se pudieron descargar las preferencias", it) }
        }
    }
}

/** Respuesta del asistente con su origen (IA o motor local). */
data class RespuestaAsistente(val texto: String, val fuente: MsgSource)

/**
 * Asistente híbrido: el motor local calcula la respuesta exacta y Gemini la explica en lenguaje natural.
 * Si la IA no está configurada o falla, se responde en modo local con el motivo.
 */
class AsistenteRepositorio(
    private val gemini: GeminiClient,
    private val local: AsistenteLocal = AsistenteLocal(),
    private val limitador: AiRateLimiter = AiRateLimiter()
) {
    val iaConfigurada: Boolean get() = gemini.isConfigured

    fun saludo() = local.saludo()

    suspend fun responder(pregunta: String, ctx: ContextoRuta, historial: List<Turn>): RespuestaAsistente {
        val base = local.responder(pregunta, ctx)
        if (!gemini.isConfigured) return RespuestaAsistente(base, MsgSource.LOCAL)
        if (!limitador.tryAcquire()) return RespuestaAsistente("$base\n\n${AiErrorMapper.fallbackNote(AiErrorMapper.RATE_LIMITED_LOCAL)}", MsgSource.LOCAL)
        return when (val r = gemini.generate(RutaAiPrompt.system(ctx, base), AiHistory.trim(historial), pregunta)) {
            is GeminiClient.Result.Ok -> AiResponseGuard.clean(r.text)?.let { RespuestaAsistente(it, MsgSource.AI) }
                ?: RespuestaAsistente("$base\n\n${AiErrorMapper.fallbackNote(AiErrorMapper.BAD_RESPONSE)}", MsgSource.LOCAL)
            is GeminiClient.Result.Fail -> {
                Bitacora.error("IA", r.reason)
                RespuestaAsistente("$base\n\n${AiErrorMapper.fallbackNote(r.reason)}", MsgSource.LOCAL)
            }
        }
    }
}

/**
 * Estado del servicio (líneas suspendidas + aviso) compartido entre el administrador y los usuarios.
 * Al aplicarlo, el planificador deja de usar las líneas suspendidas en las búsquedas.
 */
class ServicioRepositorio(
    private val remoto: FirebaseRemoto,
    private val red: EstadoRed,
    private val alternativas: ObtenerAlternativasUseCase,
    val lineas: List<Linea>
) {
    private val _estado = MutableStateFlow(EstadoServicio.NORMAL)
    val estado: StateFlow<EstadoServicio> = _estado.asStateFlow()

    /** Descarga el estado publicado. Si falla, se sigue con el último conocido (la app nunca se bloquea). */
    suspend fun actualizar() {
        if (!remoto.disponible || !red.hayRed()) return
        try {
            withContext(Dispatchers.IO) { remoto.leerEstadoServicio() }?.let { aplicar(it) }
        } catch (e: RutaSmartException) {
            Bitacora.error("Servicio", "No se pudo leer el estado del servicio: ${e.mensajeUsuario}")
        }
    }

    /** Publica un nuevo estado (solo administrador). Lanza [RutaSmartException] con un mensaje claro si falla. */
    suspend fun publicar(nuevo: EstadoServicio, uidAdmin: String) {
        val limpio = nuevo.copy(
            aviso = AdminValidador.limpiarAviso(nuevo.aviso),
            actualizadoMillis = System.currentTimeMillis(),
            actualizadoPor = uidAdmin
        )
        AdminValidador.estado(limpio, lineas)?.let { throw ServicioNoDisponibleException(it) }
        if (!red.hayRed()) throw ServicioNoDisponibleException()
        withContext(Dispatchers.IO) { remoto.guardarEstadoServicio(limpio) }
        aplicar(limpio)
        Bitacora.info("Servicio", "Estado publicado: ${limpio.lineasSuspendidas.size} líneas suspendidas, aviso=${limpio.hayAviso}")
    }

    /** Estadísticas de uso de todos los usuarios (solo administrador). */
    suspend fun resumenUso(): ResumenUso {
        if (!red.hayRed()) throw ServicioNoDisponibleException()
        val registros = withContext(Dispatchers.IO) { remoto.leerRegistrosUso() }
        return Estadisticas.calcular(registros, System.currentTimeMillis())
    }

    fun nombreLinea(id: String): String = lineas.firstOrNull { it.id == id }?.nombre ?: id

    private fun aplicar(e: EstadoServicio) {
        _estado.value = e
        alternativas.lineasSuspendidas = e.lineasSuspendidas
    }
}

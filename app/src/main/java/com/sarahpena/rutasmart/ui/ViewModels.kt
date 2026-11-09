package com.sarahpena.rutasmart.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sarahpena.rutasmart.Contenedor
import com.sarahpena.rutasmart.RutaSmartApp
import com.sarahpena.rutasmart.data.remoto.Usuario
import com.sarahpena.rutasmart.domain.admin.AdminValidador
import com.sarahpena.rutasmart.domain.admin.Administradores
import com.sarahpena.rutasmart.domain.admin.EstadoServicio
import com.sarahpena.rutasmart.domain.admin.NivelAviso
import com.sarahpena.rutasmart.domain.admin.ResumenUso
import com.sarahpena.rutasmart.domain.casosdeuso.ValidadorCuenta
import com.sarahpena.rutasmart.domain.ia.AiInputGuard
import com.sarahpena.rutasmart.domain.ia.ContextoRuta
import com.sarahpena.rutasmart.domain.ia.InputCheck
import com.sarahpena.rutasmart.domain.ia.MsgSource
import com.sarahpena.rutasmart.domain.ia.Turn
import com.sarahpena.rutasmart.domain.model.AlternativaPuntuada
import com.sarahpena.rutasmart.domain.model.ConsultaHistorial
import com.sarahpena.rutasmart.domain.model.CriteriosUsuario
import com.sarahpena.rutasmart.domain.model.Linea
import com.sarahpena.rutasmart.domain.model.Lugar
import com.sarahpena.rutasmart.domain.model.RutaSmartException
import com.sarahpena.rutasmart.domain.motor.Bitacora
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private val Application.contenedor: Contenedor get() = (this as RutaSmartApp).contenedor

// ============================================================================ Sesión

data class SesionUi(
    val usuario: Usuario? = null,
    val cargando: Boolean = false,
    val error: String? = null,
    val mensaje: String? = null,
    val firebaseDisponible: Boolean = false,
    /** true si existe admins/{uid} en Firestore. Solo muestra el menú; la seguridad real está en las reglas. */
    val esAdmin: Boolean = false
)

class SesionViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.contenedor
    private val _ui = MutableStateFlow(SesionUi(usuario = c.firebase.usuarioActual(), firebaseDisponible = c.firebase.disponible))
    val ui: StateFlow<SesionUi> = _ui.asStateFlow()

    init {
        // Sesión recordada: comprobar si sigue siendo administrador.
        _ui.value.usuario?.let { u ->
            viewModelScope.launch {
                val admin = comprobarAdmin(u)
                _ui.update { it.copy(esAdmin = admin) }
            }
        }
    }

    /** Admin = correo admin@rutasmart.com (como en Mercadito) o documento admins/{uid} en Firestore. */
    private suspend fun comprobarAdmin(u: Usuario): Boolean = when {
        u.esInvitado -> false
        Administradores.esCorreoAdmin(u.correo) -> true
        else -> withContext(Dispatchers.IO) { runCatching { c.firebase.esAdmin(u.uid) }.getOrDefault(false) }
    }

    fun limpiarMensajes() = _ui.update { it.copy(error = null, mensaje = null) }

    fun iniciarSesion(correo: String, clave: String) {
        val error = ValidadorCuenta.correo(correo) ?: ValidadorCuenta.clave(clave)
        if (error != null) { _ui.update { it.copy(error = error) }; return }
        ejecutar { c.firebase.iniciarSesion(correo.trim(), clave) }
    }

    fun registrar(correo: String, clave: String, confirmacion: String) {
        val error = ValidadorCuenta.registro(correo, clave, confirmacion)
        if (error != null) { _ui.update { it.copy(error = error) }; return }
        ejecutar { c.firebase.registrar(correo.trim(), clave) }
    }

    fun recuperarClave(correo: String) {
        ValidadorCuenta.correo(correo)?.let { e -> _ui.update { it.copy(error = e) }; return }
        viewModelScope.launch {
            _ui.update { it.copy(cargando = true, error = null) }
            try {
                withContext(Dispatchers.IO) { c.firebase.recuperarClave(correo.trim()) }
                _ui.update { it.copy(mensaje = "Te enviamos un correo para restablecer la contraseña.") }
            } catch (e: RutaSmartException) {
                _ui.update { it.copy(error = e.mensajeUsuario) }
            } finally {
                _ui.update { it.copy(cargando = false) }
            }
        }
    }

    fun entrarComoInvitado() = _ui.update { it.copy(usuario = Usuario.INVITADO, error = null) }

    fun cerrarSesion() {
        c.firebase.cerrarSesion()
        _ui.update { SesionUi(firebaseDisponible = it.firebaseDisponible) }
    }

    private fun ejecutar(accion: () -> Usuario) {
        viewModelScope.launch {
            _ui.update { it.copy(cargando = true, error = null) }
            try {
                val u = withContext(Dispatchers.IO) { accion() }
                c.historial.descargar(u)
                c.preferencias.descargar(u)
                val admin = comprobarAdmin(u)
                _ui.update { it.copy(usuario = u, esAdmin = admin) }
            } catch (e: RutaSmartException) {
                _ui.update { it.copy(error = e.mensajeUsuario) }
            } catch (e: Exception) {
                Bitacora.error("Sesion", "Error inesperado", e)
                _ui.update { it.copy(error = "Ocurrió un error inesperado. Inténtalo de nuevo.") }
            } finally {
                _ui.update { it.copy(cargando = false) }
            }
        }
    }
}

// ============================================================================ Rutas

data class RutaUi(
    val textoOrigen: String = "",
    val textoDestino: String = "",
    val origen: Lugar? = null,
    val destino: Lugar? = null,
    val ranking: List<AlternativaPuntuada> = emptyList(),
    val cargando: Boolean = false,
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class RutaViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.contenedor
    private val usuario = MutableStateFlow<Usuario?>(null)

    private val _ui = MutableStateFlow(RutaUi())
    val ui: StateFlow<RutaUi> = _ui.asStateFlow()

    val lugares: List<Lugar> get() = c.lugares

    /** Líneas suspendidas y aviso publicados por el administrador (se muestran en el inicio). */
    val estadoServicio: StateFlow<EstadoServicio> = c.servicio.estado
    fun nombreLinea(id: String): String = c.servicio.nombreLinea(id)

    /** Preferencias del usuario (Room), se actualizan solas al guardarlas. */
    val criterios: StateFlow<CriteriosUsuario> = usuario.filterNotNull()
        .flatMapLatest { c.preferencias.observar(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, CriteriosUsuario())

    val historial: StateFlow<List<ConsultaHistorial>> = usuario
        .flatMapLatest { u -> if (u == null) flowOf(emptyList()) else c.historial.observar(u) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun establecerUsuario(u: Usuario) {
        if (usuario.value?.uid == u.uid) return
        usuario.value = u
        _ui.value = RutaUi()
        viewModelScope.launch { c.historial.sincronizar(u) }
        viewModelScope.launch { c.servicio.actualizar() }
    }

    fun sugerencias(texto: String): List<Lugar> = c.buscarLugar.sugerencias(texto)

    fun cambiarOrigen(texto: String) = _ui.update { it.copy(textoOrigen = texto, origen = null, error = null) }
    fun cambiarDestino(texto: String) = _ui.update { it.copy(textoDestino = texto, destino = null, error = null) }
    fun elegirOrigen(l: Lugar) = _ui.update { it.copy(textoOrigen = l.nombre, origen = l, error = null) }
    fun elegirDestino(l: Lugar) = _ui.update { it.copy(textoDestino = l.nombre, destino = l, error = null) }
    fun intercambiar() = _ui.update {
        it.copy(textoOrigen = it.textoDestino, textoDestino = it.textoOrigen, origen = it.destino, destino = it.origen)
    }
    fun limpiarError() = _ui.update { it.copy(error = null) }

    fun guardarCriterios(nuevos: CriteriosUsuario, alTerminar: () -> Unit = {}) {
        val u = usuario.value ?: return
        viewModelScope.launch {
            try {
                nuevos.validar()
                c.preferencias.guardar(u, nuevos)
                alTerminar()
            } catch (e: RutaSmartException) {
                _ui.update { it.copy(error = e.mensajeUsuario) }
            }
        }
    }

    fun cambiarHoraPico(activa: Boolean) = guardarCriterios(criterios.value.copy(horaPico = activa))

    /** Busca alternativas; si todo sale bien guarda la consulta en el historial y llama a [alEncontrar]. */
    fun buscar(alEncontrar: () -> Unit) {
        val u = usuario.value ?: return
        val estado = _ui.value
        viewModelScope.launch {
            _ui.update { it.copy(cargando = true, error = null) }
            try {
                c.servicio.actualizar() // por si el administrador suspendió una línea hace poco
                // Si el usuario escribió el nombre sin tocar una sugerencia, se intenta reconocer.
                val origen = estado.origen ?: c.buscarLugar(estado.textoOrigen, "origen")
                val destino = estado.destino ?: c.buscarLugar(estado.textoDestino, "destino")
                val crit = criterios.value
                val ranking = withContext(Dispatchers.Default) { c.obtenerAlternativas(origen, destino, crit) }
                _ui.update { it.copy(origen = origen, destino = destino, textoOrigen = origen.nombre, textoDestino = destino.nombre, ranking = ranking) }
                c.historial.guardar(u, ConsultaHistorial.desde(UUID.randomUUID().toString(), origen, destino, ranking, crit, System.currentTimeMillis()))
                alEncontrar()
            } catch (e: RutaSmartException) {
                Bitacora.error("Rutas", e.mensajeUsuario)
                _ui.update { it.copy(error = e.mensajeUsuario) }
            } catch (e: Exception) {
                Bitacora.error("Rutas", "Error inesperado al buscar", e)
                _ui.update { it.copy(error = "Ocurrió un error inesperado al calcular las rutas.") }
            } finally {
                _ui.update { it.copy(cargando = false) }
            }
        }
    }

    fun repetir(consulta: ConsultaHistorial, alEncontrar: () -> Unit) {
        _ui.update { it.copy(textoOrigen = consulta.origen, textoDestino = consulta.destino, origen = null, destino = null) }
        buscar(alEncontrar)
    }

    fun eliminar(consulta: ConsultaHistorial) {
        val u = usuario.value ?: return
        viewModelScope.launch { c.historial.eliminar(u, consulta.id) }
    }

    fun borrarHistorial() {
        val u = usuario.value ?: return
        val ids = historial.value.map { it.id }
        viewModelScope.launch { c.historial.eliminarTodas(u, ids) }
    }

    fun sincronizar() {
        val u = usuario.value ?: return
        viewModelScope.launch { c.historial.sincronizar(u) }
    }

    fun contexto(): ContextoRuta = _ui.value.let { ContextoRuta(it.origen?.nombre, it.destino?.nombre, criterios.value, it.ranking) }

    fun explicacionLocal(): String = c.recomendar.explicar(_ui.value.ranking, criterios.value)
}

// ============================================================================ Asistente

data class Mensaje(val texto: String, val fuente: MsgSource) {
    val delUsuario: Boolean get() = fuente == MsgSource.USER
}

class AsistenteViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = app.contenedor.asistente

    val iaConfigurada: Boolean get() = repo.iaConfigurada
    val mensajes = mutableStateListOf(Mensaje(repo.saludo(), MsgSource.LOCAL))

    var escribiendo by mutableStateOf(false)
        private set

    fun enviar(texto: String, ctx: ContextoRuta) {
        if (escribiendo) return
        val limpio = when (val check = AiInputGuard.check(texto)) {
            is InputCheck.Ok -> check.text
            is InputCheck.Rejected -> {
                if (texto.isNotBlank()) mensajes.add(Mensaje(texto.trim().take(80), MsgSource.USER))
                mensajes.add(Mensaje(check.reason, MsgSource.LOCAL))
                return
            }
        }
        val historial = mensajes.map { Turn(if (it.delUsuario) Turn.USER else Turn.MODEL, it.texto) }
        mensajes.add(Mensaje(limpio, MsgSource.USER))
        escribiendo = true
        viewModelScope.launch {
            try {
                val r = repo.responder(limpio, ctx, historial)
                mensajes.add(Mensaje(r.texto, r.fuente))
            } catch (e: Exception) {
                Bitacora.error("IA", "Error inesperado del asistente", e)
                mensajes.add(Mensaje("No pude responder en este momento. Inténtalo de nuevo.", MsgSource.LOCAL))
            } finally {
                escribiendo = false
            }
        }
    }

    fun reiniciar() {
        mensajes.clear()
        mensajes.add(Mensaje(repo.saludo(), MsgSource.LOCAL))
    }
}


// ============================================================================ Administración

data class AdminUi(
    val resumen: ResumenUso? = null,
    val cargandoResumen: Boolean = false,
    val guardando: Boolean = false,
    val mensaje: String? = null,
    val error: String? = null
)

/**
 * Panel del administrador: estadísticas de uso, líneas suspendidas y avisos.
 * Todo lo que escribe pasa por las reglas de Firestore, que verifican que el usuario sea admin.
 */
class AdminViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.contenedor
    private val _ui = MutableStateFlow(AdminUi())
    val ui: StateFlow<AdminUi> = _ui.asStateFlow()

    val estado: StateFlow<EstadoServicio> = c.servicio.estado
    val lineas: List<Linea> get() = c.servicio.lineas

    fun limpiarMensajes() = _ui.update { it.copy(mensaje = null, error = null) }

    fun cargarResumen() {
        if (_ui.value.cargandoResumen) return
        viewModelScope.launch {
            _ui.update { it.copy(cargandoResumen = true, error = null) }
            try {
                c.servicio.actualizar()
                val r = c.servicio.resumenUso()
                _ui.update { it.copy(resumen = r) }
            } catch (e: RutaSmartException) {
                _ui.update { it.copy(error = e.mensajeUsuario) }
            } finally {
                _ui.update { it.copy(cargandoResumen = false) }
            }
        }
    }

    fun cambiarLinea(linea: Linea, suspender: Boolean) {
        val actual = estado.value
        val nuevas = if (suspender) actual.lineasSuspendidas + linea.id else actual.lineasSuspendidas - linea.id
        publicar(
            actual.copy(lineasSuspendidas = nuevas),
            if (suspender) "Suspendiste «${linea.nombre}». Ya no aparecerá en las rutas de los usuarios."
            else "«${linea.nombre}» volvió a estar en servicio."
        )
    }

    /** Devuelve el error de validación (para mostrarlo bajo el campo) o null si se envió a publicar. */
    fun publicarAviso(texto: String, nivel: NivelAviso): String? {
        if (texto.isBlank()) return "Escribe el aviso o usa «Quitar aviso»."
        AdminValidador.aviso(texto)?.let { return it }
        publicar(estado.value.copy(aviso = texto, nivelAviso = nivel), "Aviso publicado para todos los usuarios.")
        return null
    }

    fun quitarAviso() = publicar(estado.value.copy(aviso = "", nivelAviso = NivelAviso.INFO), "Aviso retirado.")

    private fun publicar(nuevo: EstadoServicio, ok: String) {
        val uid = c.firebase.usuarioActual()?.uid
        if (uid == null) { _ui.update { it.copy(error = "Tu sesión expiró. Vuelve a iniciar sesión.") }; return }
        viewModelScope.launch {
            _ui.update { it.copy(guardando = true, error = null, mensaje = null) }
            try {
                c.servicio.publicar(nuevo, uid)
                _ui.update { it.copy(mensaje = ok) }
            } catch (e: RutaSmartException) {
                _ui.update { it.copy(error = e.mensajeUsuario) }
            } finally {
                _ui.update { it.copy(guardando = false) }
            }
        }
    }
}

package com.sarahpena.rutasmart.data.remoto

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.sarahpena.rutasmart.domain.admin.EstadoServicio
import com.sarahpena.rutasmart.domain.admin.NivelAviso
import com.sarahpena.rutasmart.domain.admin.RegistroUso
import com.sarahpena.rutasmart.domain.model.ConsultaHistorial
import com.sarahpena.rutasmart.domain.model.CriteriosUsuario
import com.sarahpena.rutasmart.domain.model.ServicioNoDisponibleException
import com.sarahpena.rutasmart.domain.model.TipoMedio
import java.util.concurrent.TimeUnit

/** Usuario con sesión iniciada (o invitado, si Firebase no está configurado o no quiere cuenta). */
data class Usuario(val uid: String, val correo: String?, val esInvitado: Boolean) {
    companion object { val INVITADO = Usuario("invitado", null, true) }
}

/**
 * Acceso a Firebase (Authentication + Cloud Firestore).
 * Si el proyecto aún no tiene google-services.json, [disponible] es false y la app funciona solo en local.
 *
 * Estructura en Firestore:
 *   usuarios/{uid}                      → preferencias (criterios)
 *   usuarios/{uid}/consultas/{id}       → historial de búsquedas
 *   admins/{uid}                        → existe solo si el usuario es administrador (se crea a mano en la consola)
 *   config/servicio                     → estado del servicio publicado por el administrador
 */
class FirebaseRemoto(context: Context) {

    val disponible: Boolean = FirebaseApp.getApps(context).isNotEmpty()

    private val auth: FirebaseAuth? get() = if (disponible) FirebaseAuth.getInstance() else null
    private val db: FirebaseFirestore? get() = if (disponible) FirebaseFirestore.getInstance() else null

    fun usuarioActual(): Usuario? = auth?.currentUser?.let { Usuario(it.uid, it.email, false) }

    // ---------------- Autenticación (llamar desde Dispatchers.IO: espera el resultado de la tarea)

    fun registrar(correo: String, clave: String): Usuario = esperar(requireAuth().createUserWithEmailAndPassword(correo, clave)).user
        ?.let { Usuario(it.uid, it.email, false) } ?: throw ServicioNoDisponibleException("No se pudo crear la cuenta.")

    fun iniciarSesion(correo: String, clave: String): Usuario = esperar(requireAuth().signInWithEmailAndPassword(correo, clave)).user
        ?.let { Usuario(it.uid, it.email, false) } ?: throw ServicioNoDisponibleException("No se pudo iniciar sesión.")

    fun recuperarClave(correo: String) { esperar(requireAuth().sendPasswordResetEmail(correo)) }

    fun cerrarSesion() { auth?.signOut() }

    // ---------------- Firestore

    fun subirConsulta(uid: String, c: ConsultaHistorial): Boolean {
        val firestore = db ?: return false
        val datos = mapOf(
            "origen" to c.origen, "destino" to c.destino, "fechaMillis" to c.fechaMillis,
            "recomendada" to c.recomendada, "puntaje" to c.puntaje, "tiempoMinutos" to c.tiempoMinutos,
            "costo" to c.costo, "alternativas" to c.alternativas, "criterios" to c.criterios
        )
        esperar(firestore.collection("usuarios").document(uid).collection("consultas").document(c.id).set(datos))
        return true
    }

    fun eliminarConsulta(uid: String, id: String) {
        val firestore = db ?: return
        esperar(firestore.collection("usuarios").document(uid).collection("consultas").document(id).delete())
    }

    fun descargarConsultas(uid: String): List<ConsultaHistorial> {
        val firestore = db ?: return emptyList()
        val snap = esperar(firestore.collection("usuarios").document(uid).collection("consultas").get())
        return snap.documents.mapNotNull { d ->
            // Los datos externos se tratan como nulables (?.) con valores por defecto (?:).
            val origen = d.getString("origen") ?: return@mapNotNull null
            val destino = d.getString("destino") ?: return@mapNotNull null
            ConsultaHistorial(
                id = d.id, origen = origen, destino = destino,
                fechaMillis = d.getLong("fechaMillis") ?: 0L,
                recomendada = d.getString("recomendada") ?: "—",
                puntaje = d.getDouble("puntaje") ?: 0.0,
                tiempoMinutos = d.getLong("tiempoMinutos")?.toInt() ?: 0,
                costo = d.getLong("costo")?.toInt() ?: 0,
                alternativas = d.getLong("alternativas")?.toInt() ?: 0,
                criterios = d.getString("criterios") ?: "",
                sincronizada = true
            )
        }
    }

    fun guardarPreferencias(uid: String, c: CriteriosUsuario) {
        val firestore = db ?: return
        val datos = mapOf(
            "criterios" to mapOf(
                "tiempo" to c.tiempo, "costo" to c.costo, "comodidad" to c.comodidad,
                "transbordos" to c.transbordos, "ambiental" to c.ambiental, "horaPico" to c.horaPico,
                "medios" to c.mediosPermitidos.map { it.name }
            )
        )
        esperar(firestore.collection("usuarios").document(uid).set(datos, SetOptions.merge()))
    }

    fun descargarPreferencias(uid: String): CriteriosUsuario? {
        val firestore = db ?: return null
        val doc = esperar(firestore.collection("usuarios").document(uid).get())
        @Suppress("UNCHECKED_CAST")
        val m = doc.get("criterios") as? Map<String, Any?> ?: return null
        fun peso(k: String) = ((m[k] as? Number)?.toInt() ?: 0).coerceIn(0, CriteriosUsuario.PESO_MAXIMO)
        val medios = (m["medios"] as? List<*>)?.mapNotNull { n -> TipoMedio.entries.firstOrNull { it.name == n } }?.toSet()
        return CriteriosUsuario(
            peso("tiempo"), peso("costo"), peso("comodidad"), peso("transbordos"), peso("ambiental"),
            m["horaPico"] as? Boolean ?: false, medios ?: TipoMedio.entries.toSet()
        )
    }

    // ---------------- Administración

    /**
     * Un usuario es administrador si existe el documento admins/{uid}. Ese documento solo se crea desde
     * la consola de Firebase (las reglas impiden que la app lo cree), así nadie puede volverse admin solo.
     */
    fun esAdmin(uid: String): Boolean {
        val firestore = db ?: return false
        return esperar(firestore.collection("admins").document(uid).get()).exists()
    }

    /** Lee el estado del servicio (líneas suspendidas y aviso). Lo pueden leer todos, incluso invitados. */
    fun leerEstadoServicio(): EstadoServicio? {
        val firestore = db ?: return null
        val d = esperar(firestore.collection("config").document("servicio").get())
        if (!d.exists()) return EstadoServicio.NORMAL
        return EstadoServicio(
            lineasSuspendidas = (d.get("lineasSuspendidas") as? List<*>)?.filterIsInstance<String>()?.toSet() ?: emptySet(),
            aviso = d.getString("aviso") ?: "",
            nivelAviso = NivelAviso.desde(d.getString("nivel")),
            actualizadoMillis = d.getLong("actualizadoMillis") ?: 0L,
            actualizadoPor = d.getString("actualizadoPor") ?: ""
        )
    }

    /** Publica el estado del servicio. Firestore lo rechaza si quien escribe no es administrador. */
    fun guardarEstadoServicio(e: EstadoServicio) {
        val firestore = db ?: throw ServicioNoDisponibleException("Firebase no está conectado.")
        val datos = mapOf(
            "lineasSuspendidas" to e.lineasSuspendidas.sorted(),
            "aviso" to e.aviso,
            "nivel" to e.nivelAviso.name,
            "actualizadoMillis" to e.actualizadoMillis,
            "actualizadoPor" to e.actualizadoPor
        )
        esperar(firestore.collection("config").document("servicio").set(datos))
    }

    /**
     * Lee las consultas de TODOS los usuarios (consulta de grupo de colección «consultas»).
     * Solo funciona para administradores; no trae correos ni datos personales, solo datos de uso.
     */
    fun leerRegistrosUso(limite: Long = 1000): List<RegistroUso> {
        val firestore = db ?: return emptyList()
        val snap = esperar(firestore.collectionGroup("consultas").limit(limite).get())
        return snap.documents.mapNotNull { d ->
            RegistroUso(
                uid = d.reference.parent.parent?.id ?: return@mapNotNull null,
                origen = d.getString("origen") ?: return@mapNotNull null,
                destino = d.getString("destino") ?: return@mapNotNull null,
                recomendada = d.getString("recomendada") ?: "—",
                tiempoMinutos = d.getLong("tiempoMinutos")?.toInt() ?: 0,
                costo = d.getLong("costo")?.toInt() ?: 0,
                puntaje = d.getDouble("puntaje") ?: 0.0,
                fechaMillis = d.getLong("fechaMillis") ?: 0L
            )
        }
    }

    private fun requireAuth(): FirebaseAuth = auth ?: throw ServicioNoDisponibleException(
        "Firebase todavía no está conectado en este proyecto. Puedes usar la app como invitado."
    )

    /** Espera una tarea de Firebase (máx. 15 s) y traduce sus errores. Debe llamarse fuera del hilo principal. */
    private fun <T> esperar(tarea: Task<T>): T = try {
        Tasks.await(tarea, 15, TimeUnit.SECONDS)
    } catch (e: Exception) {
        val causa = e.cause ?: e
        throw when (causa) {
            is FirebaseAuthException -> ServicioNoDisponibleException(ErroresAuth.mensaje(causa.errorCode), causa)
            is FirebaseNetworkException -> ServicioNoDisponibleException(causa = causa)
            is FirebaseFirestoreException ->
                if (causa.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
                    ServicioNoDisponibleException("No tienes permiso para hacer esto. Solo el administrador puede.", causa)
                else ServicioNoDisponibleException("El servicio no respondió. Verifica tu conexión e inténtalo de nuevo.", causa)
            else -> ServicioNoDisponibleException("El servicio no respondió. Verifica tu conexión e inténtalo de nuevo.", causa)
        }
    }
}

/** Traduce los códigos de error de Firebase Authentication a mensajes en español. */
object ErroresAuth {
    fun mensaje(codigo: String?): String = when (codigo) {
        "ERROR_INVALID_EMAIL" -> "El correo no tiene un formato válido."
        "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL", "ERROR_USER_NOT_FOUND" -> "Correo o contraseña incorrectos."
        "ERROR_EMAIL_ALREADY_IN_USE" -> "Ya existe una cuenta con ese correo."
        "ERROR_WEAK_PASSWORD" -> "La contraseña es muy débil (mínimo 6 caracteres)."
        "ERROR_TOO_MANY_REQUESTS" -> "Demasiados intentos. Espera un momento e inténtalo de nuevo."
        "ERROR_OPERATION_NOT_ALLOWED" -> "El inicio con correo y contraseña no está habilitado en Firebase Console."
        else -> "No se pudo completar la operación. Inténtalo de nuevo."
    }
}

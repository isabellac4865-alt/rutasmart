package com.sarahpena.rutasmart

import android.app.Application
import android.content.Context
import android.util.Log
import com.sarahpena.rutasmart.data.local.RutaSmartDatabase
import com.sarahpena.rutasmart.data.red.RedBogota
import com.sarahpena.rutasmart.data.remoto.FirebaseRemoto
import com.sarahpena.rutasmart.data.remoto.GeminiClient
import com.sarahpena.rutasmart.data.repositorio.AsistenteRepositorio
import com.sarahpena.rutasmart.data.repositorio.EstadoRed
import com.sarahpena.rutasmart.data.repositorio.HistorialRepositorio
import com.sarahpena.rutasmart.data.repositorio.PreferenciasRepositorio
import com.sarahpena.rutasmart.data.repositorio.ServicioRepositorio
import com.sarahpena.rutasmart.domain.casosdeuso.BuscarLugarUseCase
import com.sarahpena.rutasmart.domain.casosdeuso.ObtenerAlternativasUseCase
import com.sarahpena.rutasmart.domain.casosdeuso.RecomendarRutaUseCase
import com.sarahpena.rutasmart.domain.motor.Bitacora

/** Aplicación: crea una sola vez las dependencias (inyección manual de dependencias). */
class RutaSmartApp : Application() {

    // lateinit: se inicializa en onCreate, antes de que cualquier pantalla la use.
    lateinit var contenedor: Contenedor
        private set

    override fun onCreate() {
        super.onCreate()
        // Seguridad (MASVS-STORAGE): la bitácora solo se envía a Logcat en compilaciones de depuración.
        if (BuildConfig.DEBUG) {
            Bitacora.salida = { nivel, etiqueta, mensaje ->
                if (nivel == "E") Log.e("RutaSmart/$etiqueta", mensaje) else Log.d("RutaSmart/$etiqueta", mensaje)
            }
        }
        contenedor = Contenedor(this)
    }
}

class Contenedor(context: Context) {
    private val baseDatos = RutaSmartDatabase.crear(context)
    private val estadoRed = EstadoRed(context)

    val firebase = FirebaseRemoto(context)
    val historial = HistorialRepositorio(baseDatos.consultaDao(), firebase, estadoRed)
    val preferencias = PreferenciasRepositorio(baseDatos.preferenciasDao(), firebase, estadoRed)
    val asistente = AsistenteRepositorio(GeminiClient(BuildConfig.GEMINI_API_KEY))

    private val red = RedBogota.crear()
    val lugares = RedBogota.LUGARES
    val buscarLugar = BuscarLugarUseCase(lugares)
    val obtenerAlternativas = ObtenerAlternativasUseCase(red)
    val recomendar = RecomendarRutaUseCase()
    val servicio = ServicioRepositorio(firebase, estadoRed, obtenerAlternativas, red.lineas)
}

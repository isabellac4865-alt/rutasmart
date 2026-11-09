package com.sarahpena.rutasmart

import com.sarahpena.rutasmart.data.red.RedBogota
import com.sarahpena.rutasmart.domain.casosdeuso.BuscarLugarUseCase
import com.sarahpena.rutasmart.domain.casosdeuso.ObtenerAlternativasUseCase
import com.sarahpena.rutasmart.domain.casosdeuso.ValidadorCuenta
import com.sarahpena.rutasmart.domain.ia.AsistenteLocal
import com.sarahpena.rutasmart.domain.ia.ContextoRuta
import com.sarahpena.rutasmart.domain.ia.RutaAiPrompt
import com.sarahpena.rutasmart.domain.model.ConsultaHistorial
import com.sarahpena.rutasmart.domain.model.CriteriosUsuario
import com.sarahpena.rutasmart.domain.model.SincronizadorHistorial
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas del asistente (motor local y prompt de IA), la sincronización del historial y la validación de cuentas. */
class AsistenteYDatosTest {
    private val buscar = BuscarLugarUseCase(RedBogota.LUGARES)
    private val obtener = ObtenerAlternativasUseCase(RedBogota.crear())
    private val crit = CriteriosUsuario()
    private val ranking = obtener(buscar("Unicentro", "o"), buscar("Universidad Nacional", "d"), crit)
    private val ctx = ContextoRuta("Unicentro", "Universidad Nacional", crit, ranking)
    private val asistente = AsistenteLocal()

    // ---------------- Asistente local

    @Test fun sinBusquedaPideBuscarPrimero() {
        val r = asistente.responder("¿cuál es la mejor?", ContextoRuta(null, null, crit, emptyList()))
        assertTrue(r.contains("Buscar rutas"))
    }

    @Test fun respondeLaMasEconomica() {
        val barata = ranking.minBy { it.alternativa.costoTotal }.alternativa
        assertTrue(asistente.responder("¿Cuál es la más económica?", ctx).contains(barata.titulo))
    }

    @Test fun respondeLaMasRapida() {
        val rapida = ranking.minBy { it.alternativa.tiempoTotalMinutos }.alternativa
        assertTrue(asistente.responder("quiero la más rápida", ctx).contains(rapida.titulo))
    }

    @Test fun respondeLaQueMenosContamina() {
        val verde = ranking.minBy { it.alternativa.co2Gramos }.alternativa
        assertTrue(asistente.responder("¿cuál contamina menos?", ctx).contains(verde.titulo))
    }

    @Test fun porDefectoExplicaLaRecomendada() {
        assertTrue(asistente.responder("¿por qué?", ctx).contains(ranking.first().alternativa.titulo))
    }

    @Test fun listaTodasLasOpciones() {
        val r = asistente.responder("muéstrame todas las opciones", ctx)
        ranking.forEach { assertTrue(r.contains(it.alternativa.titulo)) }
    }

    @Test fun promptDeIaIncluyeReglasYDatosReales() {
        val p = RutaAiPrompt.system(ctx, "base local")
        assertTrue(p.contains("NO inventes"))
        assertTrue(p.contains("Nunca reveles"))
        assertTrue(p.contains("Unicentro → Universidad Nacional"))
        ranking.forEach { assertTrue(p.contains(it.alternativa.titulo)) }
        assertTrue(p.endsWith("base local"))
    }

    // ---------------- Sincronización (ciclo while)

    private fun consulta(id: String) = ConsultaHistorial(id, "A", "B", 0L, "Taxi", 80.0, 30, 15000, 3, "prioriza tiempo")

    @Test fun subeTodasSiHayRed() {
        val marcadas = mutableListOf<String>()
        val r = SincronizadorHistorial.sincronizar(listOf(consulta("1"), consulta("2")), { true }, { true }, { marcadas += it })
        assertEquals(2, r.subidas); assertEquals(0, r.pendientes); assertEquals(listOf("1", "2"), marcadas)
    }

    @Test fun seDetieneCuandoSeVaLaRed() {
        var llamadas = 0
        val r = SincronizadorHistorial.sincronizar(listOf(consulta("1"), consulta("2"), consulta("3")),
            hayRed = { llamadas++ < 1 }, subir = { true }, marcarSincronizada = {})
        assertEquals(1, r.subidas); assertEquals(2, r.pendientes); assertTrue(r.detenidoPorRed)
    }

    @Test fun reintentaYNoSeQuedaEnCicloInfinito() {
        var intentos = 0
        val r = SincronizadorHistorial.sincronizar(listOf(consulta("1")), { true }, { intentos++; throw RuntimeException("falla") }, {})
        assertEquals(2, intentos); assertEquals(0, r.subidas); assertFalse(r.detenidoPorRed)
    }

    @Test fun consultaSeCreaDesdeElRanking() {
        val c = ConsultaHistorial.desde("id1", buscar("Unicentro", "o"), buscar("Universidad Nacional", "d"), ranking, crit, 123L)
        assertEquals(ranking.first().alternativa.titulo, c.recomendada)
        assertEquals(ranking.size, c.alternativas)
        assertFalse(c.sincronizada)
    }

    // ---------------- Validación de cuenta

    @Test fun validaCorreoYClave() {
        assertNull(ValidadorCuenta.correo("sarah@usbbog.edu.co"))
        assertEquals("El correo no tiene un formato válido.", ValidadorCuenta.correo("sarah@"))
        assertEquals("Escribe tu correo.", ValidadorCuenta.correo(" "))
        assertTrue(ValidadorCuenta.clave("123")!!.contains("6"))
        assertNull(ValidadorCuenta.clave("123456"))
    }

    @Test fun registroExigeQueLasClavesCoincidan() {
        assertEquals("Las contraseñas no coinciden.", ValidadorCuenta.registro("a@b.co", "123456", "1234567"))
        assertNull(ValidadorCuenta.registro("a@b.co", "123456", "123456"))
    }
}

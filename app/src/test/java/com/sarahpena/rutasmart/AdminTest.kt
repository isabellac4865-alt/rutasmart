package com.sarahpena.rutasmart

import com.sarahpena.rutasmart.data.red.RedBogota
import com.sarahpena.rutasmart.domain.admin.AdminValidador
import com.sarahpena.rutasmart.domain.admin.Administradores
import com.sarahpena.rutasmart.domain.admin.EstadoServicio
import com.sarahpena.rutasmart.domain.admin.Estadisticas
import com.sarahpena.rutasmart.domain.admin.NivelAviso
import com.sarahpena.rutasmart.domain.admin.RegistroUso
import com.sarahpena.rutasmart.domain.casosdeuso.BuscarLugarUseCase
import com.sarahpena.rutasmart.domain.casosdeuso.ObtenerAlternativasUseCase
import com.sarahpena.rutasmart.domain.model.CriteriosUsuario
import com.sarahpena.rutasmart.domain.model.TipoMedio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas del módulo de administración: estado del servicio, validaciones y estadísticas. */
class AdminTest {
    private val red = RedBogota.crear()
    private val buscar = BuscarLugarUseCase(RedBogota.LUGARES)

    // ---------------- Líneas suspendidas en el planificador

    private fun lineasUsadas(uc: ObtenerAlternativasUseCase, o: String, d: String, crit: CriteriosUsuario = CriteriosUsuario()): Set<String> =
        uc(buscar(o, "origen"), buscar(d, "destino"), crit)
            .flatMap { it.alternativa.tramos }.mapNotNull { it.linea?.id }.toSet()

    @Test fun sinSuspensionesSeUsaLaTroncalB() {
        val uc = ObtenerAlternativasUseCase(red)
        assertTrue("B" in lineasUsadas(uc, "Portal Norte", "Centro Internacional"))
    }

    @Test fun unaLineaSuspendidaNoApareceEnNingunaAlternativa() {
        val uc = ObtenerAlternativasUseCase(red)
        uc.lineasSuspendidas = setOf("B")
        val usadas = lineasUsadas(uc, "Portal Norte", "Centro Internacional")
        assertFalse("B" in usadas)
    }

    @Test fun alSuspenderSeSigueOfreciendoOtraOpcion() {
        val uc = ObtenerAlternativasUseCase(red)
        uc.lineasSuspendidas = setOf("B")
        val ranking = uc(buscar("Portal Norte", "o"), buscar("Centro Internacional", "d"), CriteriosUsuario())
        assertTrue(ranking.isNotEmpty()) // taxi, bici o SITP siguen disponibles
    }

    @Test fun alReactivarLaLineaVuelveAUsarse() {
        val uc = ObtenerAlternativasUseCase(red)
        uc.lineasSuspendidas = setOf("B")
        uc.lineasSuspendidas = emptySet()
        assertTrue("B" in lineasUsadas(uc, "Portal Norte", "Centro Internacional"))
    }

    @Test fun soloTransMilenioConTodasLasTroncalesSuspendidasNoDaRutaDeTransMilenio() {
        val uc = ObtenerAlternativasUseCase(red)
        uc.lineasSuspendidas = red.lineas.filter { it.tipo == TipoMedio.METRO }.map { it.id }.toSet()
        val usadas = lineasUsadas(uc, "Portal Norte", "Centro Internacional")
        assertTrue(usadas.none { id -> red.lineas.first { it.id == id }.tipo == TipoMedio.METRO })
    }

    // ---------------- Rol de administrador

    @Test fun elCorreoAdminEntraComoAdministradorSinImportarMayusculas() {
        assertTrue(Administradores.esCorreoAdmin("admin@rutasmart.com"))
        assertTrue(Administradores.esCorreoAdmin("  ADMIN@RutaSmart.com "))
        assertFalse(Administradores.esCorreoAdmin("sarah@gmail.com"))
        assertFalse(Administradores.esCorreoAdmin(null))
    }

    // ---------------- Validaciones de lo que publica el administrador

    @Test fun avisoVacioEsValidoPorqueSignificaQuitarlo() = assertNull(AdminValidador.aviso("   "))

    @Test fun avisoCortoLargoOConEnlaceEsRechazado() {
        assertNotNull(AdminValidador.aviso("Cierre"))
        assertNotNull(AdminValidador.aviso("x".repeat(EstadoServicio.MAX_AVISO + 1)))
        assertNotNull(AdminValidador.aviso("Más información en https://ejemplo.com"))
        assertNull(AdminValidador.aviso("Cierre de la estación Calle 72 por obras hasta las 6 p. m."))
    }

    @Test fun limpiarAvisoQuitaEspaciosYCaracteresDeControl() {
        assertEquals("Cierre en la Calle 26", AdminValidador.limpiarAviso("  Cierre \u0007 en   la Calle 26  "))
    }

    @Test fun noSePuedeSuspenderUnaLineaInexistenteNiTodasLasLineas() {
        assertNotNull(AdminValidador.estado(EstadoServicio(lineasSuspendidas = setOf("Z-99")), red.lineas))
        assertNotNull(AdminValidador.estado(EstadoServicio(lineasSuspendidas = red.lineas.map { it.id }.toSet()), red.lineas))
        assertNull(AdminValidador.estado(EstadoServicio(lineasSuspendidas = setOf("B", "SITP-7")), red.lineas))
    }

    @Test fun estadoNormalNoTieneNovedades() {
        assertFalse(EstadoServicio.NORMAL.hayNovedades)
        assertTrue(EstadoServicio(aviso = "Lluvia fuerte en el norte de la ciudad").hayNovedades)
        assertEquals(NivelAviso.INFO, NivelAviso.desde("desconocido"))
        assertEquals(NivelAviso.URGENTE, NivelAviso.desde("URGENTE"))
    }

    // ---------------- Estadísticas del panel

    private val ahora = 1_700_000_000_000L
    private val dia = 24L * 60 * 60 * 1000
    private fun r(uid: String, o: String, d: String, rec: String, t: Int, c: Int, p: Double, haceDias: Long) =
        RegistroUso(uid, o, d, rec, t, c, p, ahora - haceDias * dia)

    private val registros = listOf(
        r("u1", "Portal Norte", "Plaza de Bolívar", "Solo TransMilenio", 50, 3200, 80.0, 1),
        r("u1", "Portal Norte", "Plaza de Bolívar", "Solo TransMilenio", 54, 3200, 78.0, 2),
        r("u2", "Unicentro", "Titán Plaza", "En taxi", 25, 21000, 70.0, 3),
        r("u3", "Portal Norte", "Plaza de Bolívar", "TransMilenio + SITP", 60, 3200, 75.0, 20)
    )

    @Test fun resumenCuentaConsultasUsuariosYUltimaSemana() {
        val s = Estadisticas.calcular(registros, ahora)
        assertEquals(4, s.totalConsultas)
        assertEquals(3, s.usuariosActivos)
        assertEquals(3, s.consultasUltimos7Dias)
    }

    @Test fun rutaMasBuscadaYMedioMasRecomendadoVanPrimero() {
        val s = Estadisticas.calcular(registros, ahora)
        assertEquals("Portal Norte → Plaza de Bolívar" to 3, s.rutasMasBuscadas.first())
        assertEquals("Solo TransMilenio" to 2, s.medioMasRecomendado.first())
        assertEquals("Plaza de Bolívar" to 3, s.destinosPopulares.first())
    }

    @Test fun promediosRedondeados() {
        val s = Estadisticas.calcular(registros, ahora)
        assertEquals(47, s.tiempoPromedio)      // (50+54+25+60)/4 = 47.25
        assertEquals(7650, s.costoPromedio)     // (3200*3+21000)/4
        assertEquals(75.8, s.puntajePromedio, 0.001) // 75.75 → 75.8
    }

    @Test fun sinConsultasElResumenEstaVacio() {
        assertTrue(Estadisticas.calcular(emptyList(), ahora).vacio)
    }
}

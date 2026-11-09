package com.sarahpena.rutasmart.data.red

import com.sarahpena.rutasmart.domain.model.Linea
import com.sarahpena.rutasmart.domain.model.Lugar
import com.sarahpena.rutasmart.domain.model.Parada
import com.sarahpena.rutasmart.domain.model.TipoMedio
import com.sarahpena.rutasmart.domain.motor.RedTransporte

/**
 * Red de transporte de ejemplo de Bogotá: troncales de TransMilenio y rutas del SITP con
 * coordenadas aproximadas. Sirve para demostrar el motor de rutas sin depender de un servicio externo;
 * se puede reemplazar por datos reales (GTFS) implementando otro proveedor de [RedTransporte].
 */
object RedBogota {

    private fun p(id: String, nombre: String, lat: Double, lon: Double) = Parada(id, nombre, lat, lon)

    private val PARADAS: Array<Parada> = arrayOf(
        // --- TransMilenio: troncal Autonorte - Caracas (línea B)
        p("tm-portal-norte", "Portal Norte", 4.7546, -74.0460),
        p("tm-toberin", "Toberín", 4.7466, -74.0471),
        p("tm-alcala", "Alcalá", 4.7215, -74.0493),
        p("tm-prado", "Prado", 4.7130, -74.0510),
        p("tm-calle-100", "Calle 100", 4.6862, -74.0566),
        p("tm-heroes", "Héroes", 4.6683, -74.0583),
        p("tm-calle-72", "Calle 72", 4.6572, -74.0617),
        p("tm-calle-57", "Calle 57", 4.6441, -74.0655),
        p("tm-calle-45", "Calle 45", 4.6329, -74.0680),
        p("tm-calle-26", "Calle 26", 4.6166, -74.0715),
        p("tm-jimenez", "Av. Jiménez", 4.6021, -74.0765),
        // --- TransMilenio: troncal Calle 80 (línea D)
        p("tm-escuela-militar", "Escuela Militar", 4.6774, -74.0716),
        p("tm-av-68", "Av. 68", 4.6838, -74.0779),
        p("tm-minuto", "Minuto de Dios", 4.6929, -74.0875),
        p("tm-portal-80", "Portal 80", 4.7085, -74.1112),
        // --- TransMilenio: troncal Calle 26 (línea K)
        p("tm-centro-memoria", "Centro Memoria", 4.6181, -74.0816),
        p("tm-ciudad-universitaria", "Ciudad Universitaria", 4.6386, -74.0857),
        p("tm-can", "CAN", 4.6458, -74.0970),
        p("tm-salitre-greco", "Salitre - El Greco", 4.6532, -74.1090),
        p("tm-av-rojas", "Av. Rojas", 4.6626, -74.1196),
        p("tm-portal-dorado", "Portal El Dorado", 4.6810, -74.1290),
        // --- TransMilenio: troncal Américas (línea F)
        p("tm-sabana", "De La Sabana", 4.6080, -74.0832),
        p("tm-ricaurte", "Ricaurte", 4.6122, -74.0958),
        p("tm-puente-aranda", "Puente Aranda", 4.6230, -74.1121),
        p("tm-banderas", "Banderas", 4.6321, -74.1489),
        p("tm-portal-americas", "Portal Américas", 4.6290, -74.1719),
        // --- SITP: Calle 170 - Portal Norte
        p("sitp-usb", "Cl 172 - U. San Buenaventura", 4.7560, -74.0308),
        p("sitp-170-15", "Cl 170 - Cra 15", 4.7547, -74.0390),
        p("sitp-portal-norte", "Portal Norte (SITP)", 4.7550, -74.0452),
        p("sitp-santafe", "C.C. Santafé", 4.7625, -74.0445),
        // --- SITP: Carrera Séptima
        p("sitp-usaquen", "Usaquén - Cl 119", 4.6957, -74.0307),
        p("sitp-7-100", "Cra 7 - Cl 100", 4.6830, -74.0370),
        p("sitp-7-85", "Cra 7 - Cl 85", 4.6703, -74.0447),
        p("sitp-7-72", "Cra 7 - Cl 72", 4.6560, -74.0560),
        p("sitp-7-60", "Cra 7 - Cl 60", 4.6455, -74.0620),
        p("sitp-7-45", "Cra 7 - Cl 45", 4.6320, -74.0650),
        p("sitp-7-26", "Cra 7 - Cl 26", 4.6130, -74.0690),
        p("sitp-bolivar", "Plaza de Bolívar (SITP)", 4.5985, -74.0750),
        // --- SITP: Unicentro - Salitre
        p("sitp-unicentro", "Unicentro (SITP)", 4.7024, -74.0414),
        p("sitp-100-15", "Cl 100 - Cra 15", 4.6855, -74.0490),
        p("sitp-100-auto", "Cl 100 - Autonorte", 4.6870, -74.0575),
        p("sitp-titan", "Titán Plaza (SITP)", 4.6948, -74.0864),
        p("sitp-simon-bolivar", "Parque Simón Bolívar (SITP)", 4.6584, -74.0939),
        p("sitp-terminal", "Terminal Salitre (SITP)", 4.6576, -74.1159),
        // --- SITP: Chapinero - Gran Estación
        p("sitp-galerias", "Galerías - Cl 53", 4.6430, -74.0760),
        p("sitp-unal", "U. Nacional (paradero SITP)", 4.6385, -74.0832),
        p("sitp-gran-estacion", "Gran Estación (SITP)", 4.6466, -74.1022),
        // --- SITP: Aeropuerto
        p("sitp-portal-dorado", "Portal El Dorado (SITP)", 4.6815, -74.1300),
        p("sitp-aeropuerto", "Aeropuerto (paradero SITP)", 4.6990, -74.1420),
        // --- SITP: Plaza de las Américas
        p("sitp-plaza-americas", "Plaza de las Américas (SITP)", 4.6185, -74.1355)
    )

    private val B = Linea("B", "TransMilenio B · Autonorte–Caracas", TipoMedio.METRO)
    private val D = Linea("D", "TransMilenio D · Calle 80", TipoMedio.METRO)
    private val K = Linea("K", "TransMilenio K · Calle 26", TipoMedio.METRO)
    private val F = Linea("F", "TransMilenio F · Américas", TipoMedio.METRO)
    private val S1 = Linea("SITP-170", "SITP 170 · U. San Buenaventura–Santafé", TipoMedio.BUS)
    private val S2 = Linea("SITP-7", "SITP Séptima · Usaquén–Centro", TipoMedio.BUS)
    private val S3 = Linea("SITP-100", "SITP 100 · Unicentro–Salitre", TipoMedio.BUS)
    private val S4 = Linea("SITP-53", "SITP 53 · Chapinero–Gran Estación", TipoMedio.BUS)
    private val S5 = Linea("SITP-AERO", "SITP Aeropuerto", TipoMedio.BUS)
    private val S6 = Linea("SITP-AMERICAS", "SITP Américas · Puente Aranda–Banderas", TipoMedio.BUS)

    fun crear(): RedTransporte {
        val red = RedTransporte()
        for (parada in PARADAS) red.agregarParada(parada)
        red.agregarLinea(B, listOf("tm-portal-norte", "tm-toberin", "tm-alcala", "tm-prado", "tm-calle-100", "tm-heroes",
            "tm-calle-72", "tm-calle-57", "tm-calle-45", "tm-calle-26", "tm-jimenez"))
        red.agregarLinea(D, listOf("tm-heroes", "tm-escuela-militar", "tm-av-68", "tm-minuto", "tm-portal-80"))
        red.agregarLinea(K, listOf("tm-calle-26", "tm-centro-memoria", "tm-ciudad-universitaria", "tm-can",
            "tm-salitre-greco", "tm-av-rojas", "tm-portal-dorado"))
        red.agregarLinea(F, listOf("tm-jimenez", "tm-sabana", "tm-ricaurte", "tm-puente-aranda", "tm-banderas", "tm-portal-americas"))
        red.agregarLinea(S1, listOf("sitp-usb", "sitp-170-15", "sitp-portal-norte", "sitp-santafe"))
        red.agregarLinea(S2, listOf("sitp-usaquen", "sitp-7-100", "sitp-7-85", "sitp-7-72", "sitp-7-60", "sitp-7-45", "sitp-7-26", "sitp-bolivar"))
        red.agregarLinea(S3, listOf("sitp-unicentro", "sitp-100-15", "sitp-100-auto", "sitp-titan", "sitp-simon-bolivar", "sitp-terminal"))
        red.agregarLinea(S4, listOf("sitp-7-60", "sitp-galerias", "sitp-unal", "sitp-gran-estacion", "sitp-terminal"))
        red.agregarLinea(S5, listOf("sitp-portal-dorado", "sitp-aeropuerto"))
        red.agregarLinea(S6, listOf("tm-puente-aranda", "sitp-plaza-americas", "tm-banderas"))
        return red
    }

    /** Sitios que el usuario puede escoger como origen o destino. */
    val LUGARES: List<Lugar> = listOf(
        Lugar("Universidad de San Buenaventura", 4.7562, -74.0310, "Usaquén"),
        Lugar("Centro Comercial Santafé", 4.7626, -74.0446, "Usaquén"),
        Lugar("Portal Norte", 4.7546, -74.0460, "Usaquén"),
        Lugar("Unicentro", 4.7024, -74.0414, "Usaquén"),
        Lugar("Usaquén (parque)", 4.6950, -74.0310, "Usaquén"),
        Lugar("Parque de la 93", 4.6766, -74.0482, "Chapinero"),
        Lugar("Zona T - Centro Andino", 4.6667, -74.0527, "Chapinero"),
        Lugar("Chapinero - Calle 60", 4.6455, -74.0640, "Chapinero"),
        Lugar("Universidad Nacional", 4.6382, -74.0840, "Teusaquillo"),
        Lugar("Centro Internacional", 4.6155, -74.0685, "Santa Fe"),
        Lugar("Plaza de Bolívar", 4.5981, -74.0760, "La Candelaria"),
        Lugar("Parque Simón Bolívar", 4.6584, -74.0939, "Teusaquillo"),
        Lugar("Gran Estación", 4.6466, -74.1022, "Teusaquillo"),
        Lugar("Terminal Salitre", 4.6576, -74.1159, "Fontibón"),
        Lugar("Titán Plaza", 4.6948, -74.0864, "Engativá"),
        Lugar("Portal 80", 4.7085, -74.1112, "Engativá"),
        Lugar("Aeropuerto El Dorado", 4.7016, -74.1469, "Fontibón"),
        Lugar("Plaza de las Américas", 4.6185, -74.1355, "Kennedy"),
        Lugar("Portal Américas", 4.6290, -74.1719, "Kennedy")
    )
}

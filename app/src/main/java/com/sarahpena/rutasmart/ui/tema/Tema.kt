package com.sarahpena.rutasmart.ui.tema

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.sarahpena.rutasmart.domain.model.TipoMedio

private val Claro = lightColorScheme(
    primary = Color(0xFF1B5FD9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF001A43),
    secondary = Color(0xFFF29F05),
    onSecondary = Color(0xFF2B1700),
    secondaryContainer = Color(0xFFFFE3B0),
    tertiary = Color(0xFF13896F),
    background = Color(0xFFF6F8FC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE6EBF4)
)

private val Oscuro = darkColorScheme(
    primary = Color(0xFFADC6FF),
    onPrimary = Color(0xFF002E6B),
    primaryContainer = Color(0xFF00459A),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFFFFB94D),
    onSecondary = Color(0xFF452B00),
    secondaryContainer = Color(0xFF633F00),
    tertiary = Color(0xFF6FDBBE),
    background = Color(0xFF10131A),
    surface = Color(0xFF181C24),
    surfaceVariant = Color(0xFF2A303B)
)

@Composable
fun RutaSmartTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Oscuro else Claro, content = content)
}

/** Color con el que se dibuja cada medio en el mapa y en las etiquetas. */
fun colorDe(tipo: TipoMedio): Color = when (tipo) {
    TipoMedio.CAMINATA -> Color(0xFF7A8394)
    TipoMedio.BICICLETA -> Color(0xFF13896F)
    TipoMedio.BUS -> Color(0xFF1B5FD9)
    TipoMedio.METRO -> Color(0xFFD62828)
    TipoMedio.TAXI -> Color(0xFFE0A100)
}

package com.alexis.detectivebateria.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Colores {
    val fondo = Color(0xFF0B0F14)
    val tarjeta = Color(0xFF141B23)
    val tarjeta2 = Color(0xFF1C2530)
    val pista = Color(0xFF2A3440)
    val naranja = Color(0xFFFF8A00)
    val turquesa = Color(0xFF2EC4B6)
    val verde = Color(0xFF4CD964)
    val rojo = Color(0xFFFF4D4F)
    val amarillo = Color(0xFFFFC233)
    val texto = Color(0xFFE8EEF5)
    val tenue = Color(0xFF8A97A6)
}

@Composable
fun TemaDetective(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Colores.naranja,
            onPrimary = Color.Black,
            secondary = Colores.turquesa,
            onSecondary = Color.Black,
            background = Colores.fondo,
            onBackground = Colores.texto,
            surface = Colores.tarjeta,
            onSurface = Colores.texto,
            surfaceVariant = Colores.tarjeta2,
            onSurfaceVariant = Colores.tenue,
            surfaceContainer = Colores.tarjeta,
            error = Colores.rojo,
        ),
        content = content,
    )
}

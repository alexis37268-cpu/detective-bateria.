package com.alexis.detectivebateria.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexis.detectivebateria.datos.Gravedad

@Composable
fun Tarjeta(modifier: Modifier = Modifier, contenido: @Composable ColumnScope.() -> Unit) = Column(
    modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Colores.tarjeta).padding(16.dp),
    content = contenido,
)

@Composable
fun Titulo(texto: String) = Text(texto, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Colores.texto)

/** Barra horizontal de 0 a 1. */
@Composable
fun Barra(fraccion: Float, color: Color, modifier: Modifier = Modifier) = Box(
    modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Colores.pista),
) {
    Box(Modifier.fillMaxWidth(fraccion.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(color))
}

@Composable
fun Dato(etiqueta: String, valor: String, modifier: Modifier = Modifier, color: Color = Colores.texto) =
    Column(modifier) {
        Text(etiqueta, fontSize = 12.sp, color = Colores.tenue)
        Text(valor, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
    }

/** Etiqueta chiquita tipo "chip". */
@Composable
fun Chip(texto: String, color: Color) = Text(
    texto, fontSize = 12.sp, color = color,
    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 3.dp),
)

fun colorDe(g: Gravedad) = when (g) {
    Gravedad.BIEN -> Colores.verde
    Gravedad.OJO -> Colores.amarillo
    Gravedad.MAL -> Colores.rojo
}

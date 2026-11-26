package com.alexis.detectivebateria.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexis.detectivebateria.AppViewModel
import com.alexis.detectivebateria.datos.Formato
import com.alexis.detectivebateria.datos.Gravedad
import java.util.Locale

@Composable
fun PantallaResumen(vm: AppViewModel) {
    val a = vm.analisis ?: return
    val r = a.resumen
    var confirmarReset by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---- En vivo ----
        vm.bateria?.let { b ->
            Tarjeta {
                Titulo("Ahora mismo")
                Spacer(Modifier.height(10.dp))
                Row {
                    Dato("Carga", "${b.nivel} %", Modifier.weight(1f))
                    Dato("Temperatura", String.format(Locale("es", "AR"), "%.1f °C", b.tempC), Modifier.weight(1f),
                        color = if (b.tempC >= 40) Colores.rojo else if (b.tempC >= 35) Colores.amarillo else Colores.texto)
                    Dato("Voltaje", String.format(Locale("es", "AR"), "%.2f V", b.voltaje), Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row {
                    Dato(if (b.cargando) "Corriente (cargando)" else "Consumo actual", b.corrienteMa?.let { "$it mA" } ?: "—", Modifier.weight(1f))
                    Dato("Ciclos", b.ciclos?.toString() ?: "—", Modifier.weight(1f))
                    Dato("Estado", b.salud, Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Tip: apagá la pantalla un minuto y volvé: con el celu quieto el consumo debería bajar mucho.",
                    fontSize = 12.sp, color = Colores.tenue,
                )
            }
        }

        // ---- Diagnóstico ----
        Tarjeta {
            Titulo("Diagnóstico")
            Spacer(Modifier.height(8.dp))
            if (a.hallazgos.isEmpty()) Text("Nada raro por ahora.", color = Colores.tenue)
            a.hallazgos.forEach { h ->
                Row(Modifier.padding(vertical = 6.dp)) {
                    Icon(
                        when (h.gravedad) { Gravedad.BIEN -> Icons.Default.CheckCircle; Gravedad.OJO -> Icons.Default.Warning; Gravedad.MAL -> Icons.Default.Error },
                        null, tint = colorDe(h.gravedad), modifier = Modifier.padding(top = 2.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(h.titulo, fontWeight = FontWeight.SemiBold)
                        Text(h.detalle, fontSize = 13.sp, color = Colores.tenue)
                    }
                }
            }
        }

        // ---- Desde la última carga ----
        Tarjeta {
            Titulo("Desde la última carga completa")
            Spacer(Modifier.height(10.dp))
            Row {
                Dato("Tiempo", Formato.tiempo(r.tiempoMs), Modifier.weight(1f))
                Dato("Gastó", "${r.descargaPct} %", Modifier.weight(1f))
                Dato("Consumido", Formato.mah(r.descargaMah.takeIf { it > 0 } ?: r.consumidoMah), Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row {
                Dato("Pantalla prendida", Formato.tiempo(r.pantallaEncendidaMs), Modifier.weight(1f))
                Dato("Gasto/h prendida", r.pctHoraEncendida?.let { "%.1f %%".format(it) } ?: "—", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row {
                Dato("Pantalla apagada", Formato.tiempo(r.pantallaApagadaMs), Modifier.weight(1f))
                val v = r.pctHoraApagada
                Dato("Gasto/h apagada", v?.let { "%.1f %%".format(it) } ?: "—", Modifier.weight(1f),
                    color = when { v == null -> Colores.texto; v > 3 -> Colores.rojo; v > 1.2 -> Colores.amarillo; else -> Colores.verde })
            }
        }

        // ---- Por componente ----
        if (a.componentes.isNotEmpty()) Tarjeta {
            Titulo("¿En qué se va?")
            Text("Reparto por componente del equipo", fontSize = 12.sp, color = Colores.tenue)
            Spacer(Modifier.height(10.dp))
            val total = a.componentes.sumOf { it.mAh }.coerceAtLeast(0.01)
            a.componentes.take(8).forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.nombre, Modifier.weight(1f))
                    Text("${Formato.mah(c.mAh)} · ${(c.mAh / total * 100).toInt()} %", fontSize = 13.sp, color = Colores.tenue)
                }
                Barra((c.mAh / total).toFloat(), Colores.turquesa, Modifier.padding(top = 4.dp, bottom = 10.dp))
            }
        }

        // ---- Reiniciar ----
        Tarjeta {
            Titulo("Medir desde cero")
            Text(
                "Borra las estadísticas para arrancar una medición limpia. Lo ideal: reiniciás a la noche, " +
                    "dejás el celu sin cargar hasta la mañana y volvés a analizar.",
                fontSize = 13.sp, color = Colores.tenue,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { confirmarReset = true }) { Text("Reiniciar medición") }
        }
    }

    if (confirmarReset) AlertDialog(
        onDismissRequest = { confirmarReset = false },
        title = { Text("¿Reiniciar medición?") },
        text = { Text("Se borran las estadísticas de batería acumuladas desde la última carga. No afecta nada más.") },
        confirmButton = { TextButton(onClick = { confirmarReset = false; vm.reiniciarMedicion() }) { Text("Reiniciar") } },
        dismissButton = { TextButton(onClick = { confirmarReset = false }) { Text("Cancelar") } },
    )
}

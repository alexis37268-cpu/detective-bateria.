package com.alexis.detectivebateria.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexis.detectivebateria.AppViewModel
import com.alexis.detectivebateria.datos.Formato

/**
 * Wakelock parcial: la app le pide a Android "no apagues la CPU" aunque la pantalla esté apagada.
 * Alarma de despertar: la app programa que el celu salga del reposo a cierta hora.
 * Los dos son los culpables típicos del consumo con la pantalla apagada.
 */
@Composable
fun PantallaDespertadores(vm: AppViewModel) {
    val a = vm.analisis ?: return
    val wakelocks = a.apps.flatMap { app -> app.wakelocks.map { app.nombre to it } }
        .sortedByDescending { it.second.ms }.take(25)
    val alarmas = a.apps.flatMap { app -> app.alarmas.map { app.nombre to it } }
        .sortedByDescending { it.second.veces }.take(25)
    val maxWl = wakelocks.firstOrNull()?.second?.ms?.toFloat() ?: 1f
    val maxAl = alarmas.firstOrNull()?.second?.veces?.toFloat() ?: 1f

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Tarjeta {
                Titulo("Qué no lo deja dormir")
                Text(
                    "Wakelock: una app le pide a Android que no apague el procesador aunque la pantalla esté apagada. " +
                        "Unos minutos es normal; horas es un problema.",
                    fontSize = 13.sp, color = Colores.tenue,
                )
            }
        }
        if (wakelocks.isEmpty()) item { Text("Sin wakelocks registrados.", color = Colores.tenue) }
        items(wakelocks) { (app, wl) ->
            Tarjeta {
                Row {
                    Text(app, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Formato.tiempo(wl.ms), fontWeight = FontWeight.Bold,
                        color = if (wl.ms > 30 * 60_000L) Colores.rojo else Colores.texto)
                }
                Text(wl.nombre, fontSize = 11.sp, color = Colores.tenue, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Barra(wl.ms / maxWl, Colores.rojo, Modifier.padding(top = 6.dp))
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            Tarjeta {
                Titulo("Qué lo despierta")
                Text(
                    "Alarmas que sacan al celu del reposo. Cada despertada prende el procesador (y a veces la radio) un ratito.",
                    fontSize = 13.sp, color = Colores.tenue,
                )
            }
        }
        if (alarmas.isEmpty()) item { Text("Sin alarmas registradas.", color = Colores.tenue) }
        items(alarmas) { (app, al) ->
            Tarjeta {
                Row {
                    Text(app, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${al.veces}×", fontWeight = FontWeight.Bold)
                }
                Text(al.tag, fontSize = 11.sp, color = Colores.tenue, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Barra(al.veces / maxAl, Colores.amarillo, Modifier.padding(top = 6.dp))
            }
        }
    }
}

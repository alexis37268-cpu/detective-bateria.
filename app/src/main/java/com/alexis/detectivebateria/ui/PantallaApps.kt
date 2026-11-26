package com.alexis.detectivebateria.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexis.detectivebateria.AppViewModel
import com.alexis.detectivebateria.datos.AppConsumo
import com.alexis.detectivebateria.datos.Formato

/** Paquetes delicados: restringirlos puede romper notificaciones, llamadas, etc. */
private fun esDelicada(pkg: String) =
    pkg.startsWith("com.google.android.gms") || pkg == "com.google.android.gsf" ||
        pkg.startsWith("com.android.") || pkg == "android" || pkg.startsWith("com.motorola.") ||
        pkg.startsWith("com.qualcomm.") || pkg.startsWith("com.google.android.apps.messaging")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantallaApps(vm: AppViewModel) {
    val a = vm.analisis ?: return
    val total = a.apps.sumOf { it.mAh }.coerceAtLeast(0.01)
    var abierta by remember { mutableStateOf<Int?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "Ordenadas por consumo desde la última carga. Tocá una para ver el detalle y las acciones.",
                fontSize = 13.sp, color = Colores.tenue,
            )
        }
        items(a.apps, key = { it.uid }) { app ->
            FilaApp(vm, app, total, abierta == app.uid) { abierta = if (abierta == app.uid) null else app.uid }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilaApp(vm: AppViewModel, app: AppConsumo, total: Double, abierta: Boolean, alTocar: () -> Unit) {
    val ctx = LocalContext.current
    val pct = app.mAh / total
    Tarjeta(Modifier.animateContentSize().clickable(onClick = alTocar)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(app.nombre, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                app.paquete?.let { Text(it, fontSize = 11.sp, color = Colores.tenue, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Formato.mah(app.mAh), fontWeight = FontWeight.Bold)
                Text("${(pct * 100).toInt()} %", fontSize = 12.sp, color = Colores.tenue)
            }
        }
        Barra(pct.toFloat(), Colores.naranja, Modifier.padding(vertical = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (app.wakelockMs > 60_000) Chip("Despierto ${Formato.tiempo(app.wakelockMs)}",
                if (app.wakelockMs > 30 * 60_000L) Colores.rojo else Colores.amarillo)
            if (app.alarmasTotal > 0) Chip("Lo despertó ${app.alarmasTotal} veces", Colores.amarillo)
            if (app.movilBytes > 5 * 1024 * 1024) Chip("Datos ${Formato.bytes(app.movilBytes)}", Colores.turquesa)
            if (vm.restringidas[app.paquete] == true) Chip("Segundo plano restringido", Colores.verde)
        }

        if (abierta) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Colores.pista)
            Spacer(Modifier.height(10.dp))
            Linea("CPU usada", Formato.tiempo(app.cpuMs))
            Linea("Tareas programadas (jobs)", Formato.tiempo(app.jobsMs))
            Linea("Datos móviles", Formato.bytes(app.movilBytes))
            Linea("Wi-Fi", Formato.bytes(app.wifiBytes))

            if (app.wakelocks.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Wakelocks (lo mantienen despierto)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                app.wakelocks.sortedByDescending { it.ms }.take(5).forEach {
                    Linea(it.nombre, "${Formato.tiempo(it.ms)} · ${it.veces}×")
                }
            }
            if (app.alarmas.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Alarmas (lo despiertan)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                app.alarmas.sortedByDescending { it.veces }.take(5).forEach { Linea(it.tag, "${it.veces}×") }
            }

            val pkg = app.paquete
            if (app.esApp && pkg != null) {
                Spacer(Modifier.height(12.dp))
                if (esDelicada(pkg)) Text(
                    "⚠ App del sistema/Google: restringirla puede cortar notificaciones o funciones del celu.",
                    fontSize = 12.sp, color = Colores.amarillo,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.forzarDetencion(pkg) }) { Text("Forzar detención") }
                    Button(onClick = { vm.alternarSegundoPlano(pkg) }) {
                        Text(if (vm.restringidas[pkg] == true) "Permitir 2º plano" else "Restringir 2º plano")
                    }
                    TextButton(onClick = {
                        runCatching {
                            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
                        }
                    }) { Text("Info de la app") }
                }
            }
        }
    }
}

@Composable
fun Linea(izq: String, der: String) = Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
    Text(izq, Modifier.weight(1f), fontSize = 13.sp, color = Colores.tenue, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.width(8.dp))
    Text(der, fontSize = 13.sp)
}

package com.alexis.detectivebateria.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alexis.detectivebateria.AppViewModel
import com.alexis.detectivebateria.datos.Shell
import kotlinx.coroutines.launch

private enum class Pestania(val titulo: String, val icono: ImageVector) {
    RESUMEN("Resumen", Icons.Default.BatteryChargingFull),
    APPS("Apps", Icons.Default.Apps),
    DESPERTADORES("Despertadores", Icons.Default.Alarm),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: AppViewModel, pedirPermiso: () -> Unit) {
    var pestania by rememberSaveable { mutableStateOf(Pestania.RESUMEN) }
    val snack = remember { SnackbarHostState() }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(vm.aviso) {
        vm.aviso?.let { snack.showSnackbar(it); vm.aviso = null }
    }

    Scaffold(
        containerColor = Colores.fondo,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text("Detective Batería", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Colores.fondo),
                actions = {
                    if (vm.estado == Shell.Estado.LISTO) {
                        IconButton(onClick = {
                            scope.launch {
                                vm.aviso = "Armando informe…"
                                vm.informeParaCompartir()?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            }
                        }) { Icon(Icons.Default.Share, "Compartir informe completo") }
                        IconButton(onClick = vm::analizar, enabled = !vm.cargando) {
                            Icon(Icons.Default.Refresh, "Volver a analizar")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (vm.estado == Shell.Estado.LISTO) NavigationBar(containerColor = Colores.tarjeta) {
                Pestania.entries.forEach { p ->
                    NavigationBarItem(
                        selected = pestania == p,
                        onClick = { pestania = p },
                        icon = { Icon(p.icono, null) },
                        label = { Text(p.titulo) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                vm.estado != Shell.Estado.LISTO -> PantallaShizuku(vm, pedirPermiso)
                vm.cargando && vm.analisis == null -> Cargando()
                vm.error != null && vm.analisis == null -> Mensaje(vm.error!!, "Reintentar", vm::analizar)
                vm.analisis == null -> Mensaje("Sin datos todavía.", "Analizar", vm::analizar)
                else -> when (pestania) {
                    Pestania.RESUMEN -> PantallaResumen(vm)
                    Pestania.APPS -> PantallaApps(vm)
                    Pestania.DESPERTADORES -> PantallaDespertadores(vm)
                }
            }
            if (vm.cargando && vm.analisis != null) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun Cargando() = Column(
    Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
) {
    CircularProgressIndicator()
    Spacer(Modifier.height(16.dp))
    Text("Leyendo las estadísticas de Android…", color = Colores.tenue)
}

@Composable
private fun Mensaje(texto: String, boton: String, accion: () -> Unit) = Column(
    Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
) {
    Text(texto, color = Colores.texto)
    Spacer(Modifier.height(16.dp))
    Button(onClick = accion) { Text(boton) }
}

/** Guía para dejar Shizuku andando. */
@Composable
private fun PantallaShizuku(vm: AppViewModel, pedirPermiso: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Tarjeta {
            Text("Hace falta Shizuku", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Android no deja que una app vea cuánto gastan las demás. Shizuku corre un proceso con los mismos " +
                    "permisos que ADB y nos deja pedirle el informe de batería. No necesita root.",
                color = Colores.tenue,
            )
        }
        val paso = when {
            !vm.shizukuInstalado -> 1
            vm.estado == Shell.Estado.NO_CORRE -> 2
            vm.estado == Shell.Estado.VERSION_VIEJA -> 2
            else -> 3
        }
        Paso(1, "Instalar Shizuku desde Play Store", paso > 1) {
            if (paso == 1) Button(onClick = {
                runCatching {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=moe.shizuku.privileged.api")))
                }
            }) { Text("Abrir Play Store") }
        }
        Paso(2, "Iniciar Shizuku", paso > 2) {
            if (paso == 2) {
                Text(
                    if (vm.estado == Shell.Estado.VERSION_VIEJA) "Tu Shizuku es muy viejo: actualizalo."
                    else "1. Ajustes → Opciones de desarrollador → activá «Depuración inalámbrica» (conectado a Wi-Fi).\n" +
                        "2. Abrí Shizuku → «Emparejar» → seguí los pasos (se empareja con el código que muestra la depuración inalámbrica).\n" +
                        "3. Tocá «Iniciar».\n\nOjo: cada vez que reiniciás el celu hay que volver a tocar «Iniciar».",
                    color = Colores.tenue,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (vm.shizukuInstalado) OutlinedButton(onClick = {
                        ctx.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?.let { ctx.startActivity(it) }
                    }) { Text("Abrir Shizuku") }
                    Button(onClick = vm::actualizarEstado) { Text("Ya lo inicié") }
                }
            }
        }
        Paso(3, "Darle permiso a esta app", false) {
            if (paso == 3) Button(onClick = pedirPermiso) { Text("Dar permiso") }
        }
    }
}

@Composable
private fun Paso(n: Int, titulo: String, hecho: Boolean, contenido: @Composable ColumnScope.() -> Unit) = Tarjeta {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (hecho) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null,
            tint = if (hecho) Colores.verde else Colores.tenue,
        )
        Spacer(Modifier.width(8.dp))
        Text("$n. $titulo", fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(6.dp))
    contenido()
}

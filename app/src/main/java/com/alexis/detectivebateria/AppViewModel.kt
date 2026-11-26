package com.alexis.detectivebateria

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alexis.detectivebateria.datos.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

data class BateriaAhora(
    val nivel: Int,
    val tempC: Double,
    val voltaje: Double,
    val cargando: Boolean,
    val salud: String,
    val ciclos: Int?,
    val corrienteMa: Int?,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    var estado by mutableStateOf(Shell.Estado.NO_CORRE)
        private set
    var shizukuInstalado by mutableStateOf(false)
        private set
    var cargando by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var analisis by mutableStateOf<Analisis?>(null)
        private set
    var bateria by mutableStateOf<BateriaAhora?>(null)
        private set
    var aviso by mutableStateOf<String?>(null)

    /** paquete -> true si tiene el segundo plano restringido */
    val restringidas = mutableStateMapOf<String, Boolean>()

    private val pm: PackageManager get() = getApplication<Application>().packageManager

    init {
        // Lectura en vivo de la batería cada 2 segundos (esto no necesita Shizuku).
        viewModelScope.launch {
            while (isActive) {
                bateria = leerBateria()
                delay(2000)
            }
        }
    }

    fun actualizarEstado() {
        shizukuInstalado = try {
            pm.getPackageInfo("moe.shizuku.privileged.api", 0); true
        } catch (_: Exception) { false }
        val nuevo = Shell.estado()
        val antes = estado
        estado = nuevo
        if (nuevo == Shell.Estado.LISTO && antes != Shell.Estado.LISTO && analisis == null) analizar()
    }

    fun analizar() {
        if (cargando) return
        viewModelScope.launch {
            cargando = true
            error = null
            try {
                val lineas = ArrayList<String>(4000)
                // -c = formato CSV. Nos quedamos solo con lo que usamos (",l," y la tabla de UIDs).
                val codigo = Shell.ejecutarPorLineas("dumpsys batterystats -c") { l ->
                    if (l.contains(",l,") || l.contains(",i,uid,")) lineas.add(l)
                }
                val res = withContext(Dispatchers.Default) {
                    Analizador.parsear(lineas.asSequence()).also { a -> a.apps.forEach { it.nombre = nombreDe(it) } }
                }
                if (res.lineasUtiles == 0) {
                    error = "Android no devolvió datos (código $codigo). Probá cargar el celu un rato y volver a analizar."
                } else {
                    analisis = res
                    cargarRestricciones(res)
                }
            } catch (e: Throwable) {
                error = "No pude leer las estadísticas: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                cargando = false
            }
        }
    }

    private fun nombreDe(a: AppConsumo): String {
        when (val id = a.appId) {
            0 -> return "Kernel / root"
            1000 -> return "Sistema Android"
            1001 -> return "Radio / teléfono"
            1002 -> return "Bluetooth"
            1010 -> return "Wi-Fi"
            1013 -> return "Multimedia"
            1041 -> return "Servidor de audio"
            1047 -> return "Cámara (servicio)"
            else -> if (id in 90_000..99_999) return "Proceso aislado"
        }
        val pkg = a.paquete ?: return "UID ${a.uid}"
        val etiqueta = try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) { pkg }
        return if (a.paquetes.size > 1) "$etiqueta (+${a.paquetes.size - 1})" else etiqueta
    }

    /** Lee de AppOps si cada app ya tiene el segundo plano bloqueado. */
    private suspend fun cargarRestricciones(a: Analisis) {
        for (app in a.apps.filter { it.esApp }.take(40)) {
            val pkg = app.paquete ?: continue
            val r = runCatching { Shell.ejecutar("cmd appops get $pkg RUN_ANY_IN_BACKGROUND") }.getOrNull() ?: continue
            restringidas[pkg] = r.salida.contains("ignore")
        }
    }

    // ---------- Acciones ----------

    fun forzarDetencion(pkg: String) = accion("am force-stop $pkg", "Detenida: $pkg")

    /**
     * RUN_ANY_IN_BACKGROUND = ignore: la app no puede correr en segundo plano
     * (es lo mismo que "Restringido" en los ajustes de batería de la app). Se revierte con "allow".
     */
    fun alternarSegundoPlano(pkg: String) {
        val restringir = restringidas[pkg] != true
        val modo = if (restringir) "ignore" else "allow"
        accion("cmd appops set $pkg RUN_ANY_IN_BACKGROUND $modo",
            if (restringir) "Segundo plano restringido" else "Segundo plano permitido de nuevo") {
            restringidas[pkg] = restringir
        }
    }

    /** Borra las estadísticas para medir desde cero (por ejemplo, antes de dormir). */
    fun reiniciarMedicion() = accion("dumpsys batterystats --reset",
        "Medición reiniciada. Usá el celu normal unas horas (o dejalo toda la noche) y volvé a analizar.") {
        analisis = null
    }

    private fun accion(cmd: String, ok: String, despues: () -> Unit = {}) {
        viewModelScope.launch {
            val r = runCatching { Shell.ejecutar(cmd) }
            aviso = r.fold(
                { if (it.ok) { despues(); ok } else "Falló: ${it.error.ifBlank { it.salida }.take(200)}" },
                { "Falló: ${it.message}" },
            )
        }
    }

    /** Guarda el informe completo legible y devuelve un Intent para compartirlo (por ej. a Claude). */
    suspend fun informeParaCompartir(): Intent? = withContext(Dispatchers.IO) {
        runCatching {
            val ctx = getApplication<Application>()
            val dir = File(ctx.cacheDir, "informes").apply { mkdirs() }
            val archivo = File(dir, "batterystats.txt")
            archivo.bufferedWriter().use { w ->
                Shell.ejecutarPorLineas("dumpsys batterystats --charged") { w.write(it); w.newLine() }
                w.newLine(); w.write("===== dumpsys battery ====="); w.newLine()
                w.write(Shell.ejecutar("dumpsys battery").salida)
            }
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.archivos", archivo)
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "Compartir informe",
            )
        }.getOrElse { aviso = "No pude armar el informe: ${it.message}"; null }
    }

    // ---------- Batería en vivo ----------

    private fun leerBateria(): BateriaAhora? {
        val ctx = getApplication<Application>()
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val nivel = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val estadoCarga = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val salud = when (i.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Buena"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Sobrecalentada"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Muerta"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Sobrevoltaje"
            BatteryManager.BATTERY_HEALTH_COLD -> "Fría"
            else -> "Sin dato"
        }
        val ciclos = if (Build.VERSION.SDK_INT >= 34)
            i.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1).takeIf { it >= 0 } else null

        // Corriente instantánea. Cada fabricante la informa distinto: unos en µA, otros en mA,
        // y el signo (cargando/descargando) también varía. Por eso usamos el valor absoluto.
        val bm = ctx.getSystemService(BatteryManager::class.java)
        val crudo = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: Int.MIN_VALUE
        val corriente = when {
            crudo == Int.MIN_VALUE || crudo == 0 -> null
            abs(crudo) > 20_000 -> abs(crudo) / 1000 // venía en µA
            else -> abs(crudo)
        }
        return BateriaAhora(
            nivel = nivel,
            tempC = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0,
            voltaje = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) / 1000.0,
            cargando = estadoCarga == BatteryManager.BATTERY_STATUS_CHARGING || estadoCarga == BatteryManager.BATTERY_STATUS_FULL,
            salud = salud,
            ciclos = ciclos,
            corrienteMa = corriente,
        )
    }
}

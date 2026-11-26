package com.alexis.detectivebateria.datos

/*
 * `dumpsys batterystats -c` devuelve el informe en formato "checkin": CSV, una línea por dato.
 *
 *   9,10234,l,pwi,uid,35.2,0,12.1,0
 *   │   │   │  │   └── datos (dependen del tipo)
 *   │   │   │  └────── tipo de línea (pwi = consumo, wl = wakelock, wua = alarma, ...)
 *   │   │   └───────── categoría: i = info, l = desde la última carga completa
 *   │   └───────────── UID: el "número de usuario" Linux de cada app (las apps arrancan en 10000)
 *   └───────────────── versión del formato
 *
 * Es mucho más fácil de procesar que el informe "lindo" para humanos.
 */

data class Wakelock(val nombre: String, val ms: Long, val veces: Int)
data class Alarma(val tag: String, val veces: Int)

class AppConsumo(val uid: Int) {
    val paquetes = mutableListOf<String>()
    var nombre: String = "UID $uid"
    var mAh = 0.0
    val wakelocks = mutableListOf<Wakelock>()
    val alarmas = mutableListOf<Alarma>()
    var cpuMs = 0L
    var movilBytes = 0L
    var wifiBytes = 0L
    var jobsMs = 0L

    /** Tiempo que mantuvo el celu despierto con wakelocks parciales (CPU prendida, pantalla apagada). */
    val wakelockMs get() = wakelocks.sumOf { it.ms }
    val alarmasTotal get() = alarmas.sumOf { it.veces }

    /** El UID "base" (sin el número de usuario/perfil). Apps normales: 10000 a 19999. */
    val appId get() = uid % 100_000
    val esApp get() = appId in 10_000..19_999
    val paquete get() = paquetes.firstOrNull()
}

data class Componente(val clave: String, val mAh: Double) {
    val nombre get() = NOMBRES[clave] ?: clave

    companion object {
        val NOMBRES = mapOf(
            "scrn" to "Pantalla", "cpu" to "Procesador", "blue" to "Bluetooth",
            "camera" to "Cámara", "audio" to "Audio", "video" to "Video",
            "flashlight" to "Linterna", "cell" to "Red móvil (señal)", "sensors" to "Sensores",
            "gnss" to "GPS", "wifi" to "Wi-Fi", "memory" to "Memoria", "phone" to "Llamadas",
            "ambi" to "Pantalla ambiente", "idle" to "Reposo", "user" to "Otros usuarios",
            "unacc" to "Sin contabilizar", "over" to "Sobrecontado", "wakelock" to "Wakelocks",
            "sync" to "Sincronización", "mobile_radio" to "Red móvil",
        )
    }
}

data class Resumen(
    val tiempoMs: Long,
    val pantallaApagadaMs: Long,
    val descargaPct: Int,
    val descargaPantallaOnPct: Int,
    val descargaPantallaOffPct: Int,
    val descargaMah: Double,
    val capacidadDisenoMah: Double,
    val capacidadEstimadaMah: Double,
    val aprendidaMaxMah: Double,
    val consumidoMah: Double,
) {
    val pantallaEncendidaMs get() = (tiempoMs - pantallaApagadaMs).coerceAtLeast(0)

    /** % que pierde por hora con la pantalla apagada. Lo normal: menos de 1 %/h. */
    val pctHoraApagada: Double?
        get() = if (pantallaApagadaMs >= 30 * 60_000L) descargaPantallaOffPct / (pantallaApagadaMs / 3.6e6) else null

    val pctHoraEncendida: Double?
        get() = if (pantallaEncendidaMs >= 15 * 60_000L) descargaPantallaOnPct / (pantallaEncendidaMs / 3.6e6) else null

    /**
     * Salud: capacidad que el cargador "aprendió" midiendo cargas reales, contra la de fábrica.
     * No todos los celulares lo informan (si no, da 0 y no mostramos nada).
     */
    val capacidadReferencia get() = if (capacidadDisenoMah > 0) capacidadDisenoMah else capacidadEstimadaMah

    val saludPct: Double?
        get() {
            if (aprendidaMaxMah <= 0 || capacidadReferencia <= 0) return null
            return (aprendidaMaxMah / capacidadReferencia * 100).coerceAtMost(120.0)
        }
}

enum class Gravedad { BIEN, OJO, MAL }
data class Hallazgo(val gravedad: Gravedad, val titulo: String, val detalle: String)

class Analisis(
    val resumen: Resumen,
    val apps: List<AppConsumo>,
    val componentes: List<Componente>,
    val lineasUtiles: Int,
) {
    val hallazgos: List<Hallazgo> by lazy { Diagnostico.armar(this) }
}

object Analizador {

    private fun String.num() = toDoubleOrNull() ?: 0.0
    private fun String.lng() = toLongOrNull() ?: toDoubleOrNull()?.toLong() ?: 0L

    /** Capacidades "aprendidas" vienen en µAh; si el número es enorme lo pasamos a mAh. */
    private fun aMah(v: Double) = if (v > 100_000) v / 1000.0 else v

    fun parsear(lineas: Sequence<String>): Analisis {
        val apps = HashMap<Int, AppConsumo>()
        fun app(uid: Int) = apps.getOrPut(uid) { AppConsumo(uid) }
        val uidPaquetes = HashMap<Int, MutableList<String>>()
        val componentes = mutableListOf<Componente>()
        var bt: List<String> = emptyList()
        var dc: List<String> = emptyList()
        var pws: List<String> = emptyList()
        var utiles = 0

        for (linea in lineas) {
            val c = linea.split(',')
            if (c.size < 5) continue
            val uid = c[1].toIntOrNull() ?: continue
            val categoria = c[2]
            val tipo = c[3]
            val d = c.subList(4, c.size)

            // Tabla UID -> nombre de paquete
            if (categoria == "i" && tipo == "uid" && d.size >= 2) {
                d[0].toIntOrNull()?.let { uidPaquetes.getOrPut(it) { mutableListOf() }.add(d[1]) }
                continue
            }
            if (categoria != "l") continue
            utiles++

            when (tipo) {
                "bt" -> bt = d
                "dc" -> dc = d
                "pws" -> pws = d
                // Consumo: "uid" = una app; cualquier otra etiqueta = componente del equipo (pantalla, radio...)
                "pwi" -> if (d.size >= 2) {
                    val mah = d[1].num()
                    if (d[0] == "uid") app(uid).mAh += mah
                    else if (mah > 0) componentes.add(Componente(d[0], mah))
                }
                // Wakelock: nombre, luego grupos "tiempo,tipo,veces,...". El tipo "p" = parcial.
                "wl" -> {
                    val i = d.indexOf("p")
                    if (i >= 2 && i + 1 < d.size) {
                        val ms = d[i - 1].lng()
                        if (ms > 0) app(uid).wakelocks.add(Wakelock(d[0], ms, d[i + 1].toIntOrNull() ?: 0))
                    }
                }
                // Alarma que despierta al celu: tag, veces
                "wua" -> if (d.size >= 2) {
                    val veces = d.last().toIntOrNull() ?: 0
                    if (veces > 0) app(uid).alarmas.add(Alarma(d.dropLast(1).joinToString(","), veces))
                }
                "cpu" -> if (d.size >= 2) app(uid).cpuMs += d[0].lng() + d[1].lng()
                "nt" -> if (d.size >= 4) {
                    val a = app(uid)
                    a.movilBytes += d[0].lng() + d[1].lng()
                    a.wifiBytes += d[2].lng() + d[3].lng()
                }
                "jb" -> if (d.size >= 3) app(uid).jobsMs += d[1].lng()
            }
        }

        // Asignar paquetes a cada UID
        for (a in apps.values) {
            (uidPaquetes[a.uid] ?: uidPaquetes[a.appId])?.let { a.paquetes.addAll(it.distinct()) }
        }

        // Campos de "bt": 1 = tiempo con batería, 6 = tiempo pantalla apagada (ms),
        // 8 = capacidad estimada, 10 = máx. capacidad aprendida.
        // Campos de "dc": 0 = % descargado, 2 = % con pantalla on, 3 = % con pantalla off, 4 = mAh descargados.
        val resumen = Resumen(
            tiempoMs = bt.getOrNull(1)?.lng() ?: 0,
            pantallaApagadaMs = bt.getOrNull(6)?.lng() ?: 0,
            descargaPct = dc.getOrNull(1)?.toIntOrNull() ?: dc.getOrNull(0)?.toIntOrNull() ?: 0,
            descargaPantallaOnPct = dc.getOrNull(2)?.toIntOrNull() ?: 0,
            descargaPantallaOffPct = dc.getOrNull(3)?.toIntOrNull() ?: 0,
            descargaMah = dc.getOrNull(4)?.num() ?: 0.0,
            capacidadDisenoMah = pws.getOrNull(0)?.num() ?: 0.0,
            capacidadEstimadaMah = bt.getOrNull(8)?.num() ?: 0.0,
            aprendidaMaxMah = aMah(bt.getOrNull(10)?.num() ?: 0.0),
            consumidoMah = pws.getOrNull(1)?.num() ?: 0.0,
        )

        val lista = apps.values
            .filter { it.mAh >= 0.1 || it.wakelockMs > 60_000 || it.alarmasTotal >= 10 }
            .sortedByDescending { it.mAh }

        return Analisis(resumen, lista, componentes.sortedByDescending { it.mAh }, utiles)
    }
}

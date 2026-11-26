package com.alexis.detectivebateria.datos

import java.util.Locale

/** Convierte los números en conclusiones en criollo. Los umbrales están explicados en cada caso. */
object Diagnostico {

    fun armar(a: Analisis): List<Hallazgo> {
        val r = a.resumen
        val lista = mutableListOf<Hallazgo>()

        // 1) Salud de la batería: por debajo de ~80 % de la capacidad original se nota mucho.
        r.saludPct?.let { s ->
            val txt = "Capacidad real ≈ ${r.aprendidaMaxMah.toInt()} mAh de ${r.capacidadReferencia.toInt()} mAh de fábrica (${s.f0()} %)."
            lista += when {
                s < 75 -> Hallazgo(Gravedad.MAL, "Batería gastada", "$txt Con esto ninguna app lo arregla: toca cambiar la batería. Además, con la batería gastada el voltaje cae en los picos y el celu se apaga solo o se pone lento.")
                s < 85 -> Hallazgo(Gravedad.OJO, "Batería con desgaste", "$txt Está gastada pero todavía sirve.")
                else -> Hallazgo(Gravedad.BIEN, "Batería sana", txt)
            }
        }

        // 2) Consumo en reposo: un celu sano pierde menos de 1 %/h con la pantalla apagada.
        r.pctHoraApagada?.let { v ->
            val txt = "Con la pantalla apagada pierde ${v.f1()} % por hora (${Formato.tiempo(r.pantallaApagadaMs)} apagada, ${r.descargaPantallaOffPct} % en total)."
            lista += when {
                v > 3 -> Hallazgo(Gravedad.MAL, "Algo se la come con la pantalla apagada", "$txt Lo normal es menos de 1 %/h. Mirá la pestaña Despertadores: alguna app no lo deja dormir.")
                v > 1.2 -> Hallazgo(Gravedad.OJO, "Consumo en reposo algo alto", "$txt Lo normal es menos de 1 %/h.")
                else -> Hallazgo(Gravedad.BIEN, "Reposo normal", "$txt Está bien: duerme como corresponde.")
            }
        }

        // 3) Apps que lo mantienen despierto: más de 30 min de wakelock parcial es sospechoso.
        a.apps.filter { it.esApp && it.wakelockMs > 30 * 60_000L }
            .sortedByDescending { it.wakelockMs }.take(3).forEach { app ->
                lista += Hallazgo(
                    if (app.wakelockMs > 2 * 3_600_000L) Gravedad.MAL else Gravedad.OJO,
                    "${app.nombre} no lo deja dormir",
                    "Lo mantuvo despierto ${Formato.tiempo(app.wakelockMs)} desde la última carga. Probá restringirle el segundo plano.",
                )
            }

        // 4) Apps que lo despiertan seguido: más de ~20 veces por hora es exagerado.
        val horas = (r.tiempoMs / 3.6e6).coerceAtLeast(1.0)
        a.apps.filter { it.esApp && it.alarmasTotal / horas > 20 }
            .sortedByDescending { it.alarmasTotal }.take(3).forEach { app ->
                lista += Hallazgo(
                    Gravedad.OJO,
                    "${app.nombre} lo despierta seguido",
                    "${app.alarmasTotal} veces (${(app.alarmasTotal / horas).f0()} por hora).",
                )
            }

        // 5) Qué componente se lleva más
        val total = a.componentes.sumOf { it.mAh }
        a.componentes.firstOrNull()?.takeIf { total > 0 }?.let { c ->
            val pct = c.mAh / total * 100
            if (c.clave == "cell" && pct > 25) lista += Hallazgo(
                Gravedad.OJO, "La señal móvil gasta mucho",
                "La red móvil se lleva ${pct.f0()} %. Pasa con mala señal (el módem sube la potencia). Si tenés Wi-Fi, usalo; en zonas de mala señal probá forzar 4G en vez de 5G.",
            )
        }

        if (r.tiempoMs < 2 * 3_600_000L) lista += Hallazgo(
            Gravedad.OJO, "Pocos datos todavía",
            "Hace ${Formato.tiempo(r.tiempoMs)} que no se carga completo. Para un diagnóstico confiable usalo varias horas (ideal: una noche entera) y volvé a analizar.",
        )

        return lista.sortedBy { it.gravedad.ordinal * -1 }
    }

    private fun Double.f0() = String.format(Locale("es", "AR"), "%.0f", this)
    private fun Double.f1() = String.format(Locale("es", "AR"), "%.1f", this)
}

object Formato {
    fun tiempo(ms: Long): String {
        val min = ms / 60_000
        return when {
            ms < 60_000 -> "${ms / 1000} s"
            min < 60 -> "$min min"
            min % 60 == 0L -> "${min / 60} h"
            else -> "${min / 60} h ${min % 60} min"
        }
    }

    fun bytes(b: Long): String = when {
        b < 1024 * 1024 -> "${b / 1024} KB"
        b < 1024L * 1024 * 1024 -> "${b / (1024 * 1024)} MB"
        else -> String.format(Locale("es", "AR"), "%.1f GB", b / (1024.0 * 1024 * 1024))
    }

    fun mah(v: Double) = if (v < 10) String.format(Locale("es", "AR"), "%.1f mAh", v) else "${v.toInt()} mAh"
}

package com.alexis.detectivebateria.datos

import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * Ejecuta comandos como el usuario "shell" (el mismo que usa ADB) a través de Shizuku.
 *
 * ¿Por qué hace falta? Android aísla cada app: una app común no puede leer cuánto gastaron
 * las demás. El usuario "shell" sí tiene el permiso DUMP, que es lo que usa `dumpsys`.
 * Shizuku corre un proceso con ese usuario y nos deja mandarle comandos.
 */
object Shell {

    enum class Estado { NO_CORRE, VERSION_VIEJA, SIN_PERMISO, LISTO }

    fun estado(): Estado = try {
        when {
            !Shizuku.pingBinder() -> Estado.NO_CORRE
            Shizuku.isPreV11() -> Estado.VERSION_VIEJA
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> Estado.SIN_PERMISO
            else -> Estado.LISTO
        }
    } catch (_: Throwable) {
        Estado.NO_CORRE
    }

    fun pedirPermiso(codigo: Int) = Shizuku.requestPermission(codigo)

    data class Resultado(val codigo: Int, val salida: String, val error: String) {
        val ok get() = codigo == 0
    }

    /**
     * Shizuku 13 dejó `newProcess` como privado (recomiendan un "UserService" con AIDL),
     * pero para comandos simples sigue funcionando bien llamándolo por reflexión.
     */
    private fun crearProceso(comando: String): Process {
        val metodo = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        metodo.isAccessible = true
        return metodo.invoke(null, arrayOf("sh", "-c", comando), null, null) as Process
    }

    /** Corre el comando y devuelve todo el texto. Para salidas chicas. */
    suspend fun ejecutar(comando: String): Resultado = withContext(Dispatchers.IO) {
        coroutineScope {
            val p = crearProceso(comando)
            // stdout y stderr se leen en paralelo: si uno se llena y nadie lo lee, el proceso se traba.
            val err = async { p.errorStream.bufferedReader().readText() }
            val out = p.inputStream.bufferedReader().readText()
            val codigo = p.waitFor()
            Resultado(codigo, out, err.await())
        }
    }

    /** Corre el comando y pasa cada línea a [porLinea] sin guardar todo en memoria. */
    suspend fun ejecutarPorLineas(comando: String, porLinea: (String) -> Unit): Int =
        withContext(Dispatchers.IO) {
            coroutineScope {
                val p = crearProceso(comando)
                val err = async { p.errorStream.bufferedReader().readText() }
                p.inputStream.bufferedReader().useLines { it.forEach(porLinea) }
                val codigo = p.waitFor()
                err.await()
                codigo
            }
        }
}

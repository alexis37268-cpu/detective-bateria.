package com.alexis.detectivebateria

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.alexis.detectivebateria.datos.Shell
import com.alexis.detectivebateria.ui.App
import com.alexis.detectivebateria.ui.TemaDetective
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    // Shizuku avisa por "listeners" cuándo está disponible, cuándo se cae y cuándo respondimos el permiso.
    private val alRecibirBinder = Shizuku.OnBinderReceivedListener { vm.actualizarEstado() }
    private val alMorirBinder = Shizuku.OnBinderDeadListener { vm.actualizarEstado() }
    private val alResponderPermiso = Shizuku.OnRequestPermissionResultListener { _, _ -> vm.actualizarEstado() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addBinderReceivedListenerSticky(alRecibirBinder)
        Shizuku.addBinderDeadListener(alMorirBinder)
        Shizuku.addRequestPermissionResultListener(alResponderPermiso)

        setContent {
            TemaDetective {
                App(vm = vm, pedirPermiso = ::pedirPermisoShizuku)
            }
        }
    }

    private fun pedirPermisoShizuku() {
        runCatching { Shell.pedirPermiso(CODIGO_PERMISO) }
            .onFailure { vm.aviso = "Shizuku no está corriendo" }
    }

    override fun onResume() {
        super.onResume()
        vm.actualizarEstado()
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(alRecibirBinder)
        Shizuku.removeBinderDeadListener(alMorirBinder)
        Shizuku.removeRequestPermissionResultListener(alResponderPermiso)
        super.onDestroy()
    }

    companion object { const val CODIGO_PERMISO = 37 }
}

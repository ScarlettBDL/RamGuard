package io.github.scarlettbdl.ramguard

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.scarlettbdl.ramguard.ui.RamGuardTheme
import io.github.scarlettbdl.ramguard.ui.RamScreen
import io.github.scarlettbdl.ramguard.vm.RamViewModel
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RamGuardTheme {
                val vm: RamViewModel = viewModel()
                ShizukuBridge(vm)
                RamScreen(vm)
            }
        }
    }
}

/** Conecta los callbacks de Shizuku con el ViewModel mientras la UI vive. */
@Composable
private fun ShizukuBridge(vm: RamViewModel) {
    DisposableEffect(vm) {
        val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == RamViewModel.SHIZUKU_REQUEST_CODE) {
                vm.onShizukuPermissionResult(grantResult == PackageManager.PERMISSION_GRANTED)
            }
        }
        val receivedListener = Shizuku.OnBinderReceivedListener {
            vm.onShizukuBinderChanged()
        }
        val deadListener = Shizuku.OnBinderDeadListener {
            vm.onShizukuBinderChanged()
        }

        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListenerSticky(receivedListener)
        Shizuku.addBinderDeadListener(deadListener)

        onDispose {
            Shizuku.removeRequestPermissionResultListener(permissionListener)
            Shizuku.removeBinderReceivedListener(receivedListener)
            Shizuku.removeBinderDeadListener(deadListener)
        }
    }
}

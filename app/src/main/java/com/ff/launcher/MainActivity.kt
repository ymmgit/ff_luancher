package com.ff.launcher

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ff.launcher.ui.LauncherRoot
import com.ff.launcher.ui.theme.FFLauncherTheme
import com.ff.launcher.viewmodel.LauncherViewModel
import com.ff.launcher.viewmodel.WidgetRequest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    private val widgetBindLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.d("MainActivity", "widgetBindLauncher result: ${result.resultCode}, data: ${result.data}")
            viewModel.onBindPermissionResult(result.resultCode, result.data)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FFLauncherTheme {
                LauncherRoot(viewModel = viewModel)
            }
        }
        closeSecondScreenForHomeIntent(intent)
        // Route widget picker intents to the registered launcher. Configure pages take a
        // different path: AppWidgetHost.startAppWidgetConfigureActivityForResult (see
        // onActivityResult), because the system must grant access to non-exported
        // configure activities a plain Intent launch would crash on.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.widgetRequests.collect { request ->
                    Log.d("MainActivity", "widgetRequests received: $request")
                    when (request) {
                        is WidgetRequest.BindPermission ->
                            widgetBindLauncher.launch(request.intent)
                        is WidgetRequest.Configure ->
                            viewModel.startWidgetConfigure(
                                this@MainActivity,
                                request.appWidgetId,
                                REQUEST_CONFIGURE_WIDGET,
                            )
                    }
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d("MainActivity", "onActivityResult: requestCode=$requestCode, resultCode=$resultCode")
        if (requestCode == REQUEST_CONFIGURE_WIDGET) {
            viewModel.onWidgetConfigureResult(resultCode, data)
        } else if (requestCode == REQUEST_BIND_WIDGET) {
            viewModel.onBindPermissionResult(resultCode, data)
        }
    }

    private companion object {
        private const val REQUEST_CONFIGURE_WIDGET = 1001
        private const val REQUEST_BIND_WIDGET = 1002
    }

    override fun onResume() {
        super.onResume()
        viewModel.setHostResumed(true)
        // Re-check the default-home role: the user may have just confirmed the system
        // dialog (or changed it in system settings).
        viewModel.refreshDefaultLauncherState()
    }

    override fun onPause() {
        super.onPause()
        viewModel.setHostResumed(false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Return mechanic #2: the bottom-up Home gesture re-delivers ACTION_MAIN +
        // CATEGORY_HOME to this singleTask activity — collapse the Secondary Screen.
        closeSecondScreenForHomeIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        // Do not force-close the Secondary Screen onStop: when the user taps a widget
        // and later taps Back, they return to the Secondary Screen they launched it from.
    }

    private fun closeSecondScreenForHomeIntent(intent: Intent?) {
        if (intent != null &&
            intent.action == Intent.ACTION_MAIN &&
            intent.hasCategory(Intent.CATEGORY_HOME)
        ) {
            viewModel.closeSecondary()
        }
    }
}

package com.nanobuo.mypodder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nanobuo.mypodder.ui.MainViewModel
import com.nanobuo.mypodder.ui.nav.MyPodderApp
import com.nanobuo.mypodder.ui.theme.MyPodderTheme
import com.nanobuo.mypodder.ui.theme.rememberCoverSeed
import com.nanobuo.mypodder.ui.theme.isDarkTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    /** Incrementato quando si apre l'app dalla notifica: mostra il player a schermo intero. */
    private val openPlayerRequests = mutableIntStateOf(0)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && savedInstanceState == null &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val s = settings ?: return@setContent
            val coverUrl by viewModel.nowPlayingCover.collectAsStateWithLifecycle()
            val seed = if (s.coverColors) rememberCoverSeed(coverUrl) else null
            MyPodderTheme(
                darkTheme = isDarkTheme(s.theme),
                dynamicColor = s.dynamicColor,
                coverColors = s.coverColors,
                seedColor = seed,
            ) {
                MyPodderApp(viewModel, openPlayerRequests.intValue)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.player.connect()
    }

    override fun onStop() {
        viewModel.player.disconnect()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_PLAYER, false) == true) {
            intent.removeExtra(EXTRA_OPEN_PLAYER)
            openPlayerRequests.intValue++
        }
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "open_player"
    }
}

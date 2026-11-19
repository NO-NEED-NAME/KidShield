package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ui.ModirManApp
import com.example.ui.theme.ModirManTheme
import com.example.viewmodel.ModirManViewModel

class MainActivity : AppCompatActivity() {

    private val viewModel: ModirManViewModel by viewModels()
    private var pendingTileAction by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingTileAction = intent?.getStringExtra(EXTRA_TILE_ACTION)

        setContent {
            ModirManTheme(darkTheme = true) {
                ModirManApp(
                    viewModel = viewModel,
                    activity = this,
                    pendingTileAction = pendingTileAction,
                    onTileActionConsumed = { pendingTileAction = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val tileAction = intent.getStringExtra(EXTRA_TILE_ACTION)
        if (tileAction != null) {
            pendingTileAction = tileAction
        }
    }

    companion object {
        const val EXTRA_TILE_ACTION = "extra_tile_action"
        const val ACTION_FROM_TILE_UNLOCK = "action_from_tile_unlock"
        const val ACTION_FROM_TILE_LOCK = "action_from_tile_lock"
    }
}

package com.fmhub24.android

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.fmhub24.android.data.UserPreferencesRepository
import com.fmhub24.android.ui.FMHuB24Root
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var preferences: UserPreferencesRepository

    @Volatile private var autoMiniplayerEnabled = false
    @Volatile private var playerActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            preferences.settings.collect { settings ->
                autoMiniplayerEnabled = settings.autoMiniplayer
                updatePictureInPictureParams()
            }
        }
        setContent {
            FMHuB24Root()
        }
    }

    /** Called by the player destination so PiP never starts on unrelated screens. */
    fun setPlayerActive(active: Boolean) {
        playerActive = active
        updatePictureInPictureParams()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.O until Build.VERSION_CODES.S &&
            playerActive && autoMiniplayerEnabled
        ) {
            enterPictureInPictureMode(pipParams())
        }
    }

    private fun updatePictureInPictureParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setPictureInPictureParams(pipParams(autoEnter = playerActive && autoMiniplayerEnabled))
        }
    }

    private fun pipParams(autoEnter: Boolean = false): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter)
        }
        return builder.build()
    }
}

package com.pradyu.talkingtom

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.pradyu.talkingtom.audio.AudioEngine
import com.pradyu.talkingtom.audio.TomState
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private lateinit var audioEngine: AudioEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioEngine = AudioEngine(this)

        setContent {
            TalkingTomApp(audioEngine)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioEngine.stop()
    }
}

@Composable
fun TalkingTomApp(audioEngine: AudioEngine) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { hasPermission = it }
    )

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            hasPermission = true
        } else {
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    MaterialTheme {
        if (hasPermission) {
            TomScreen(audioEngine)
        } else {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text("Need Mic Permission")
            }
        }
    }
}

@Composable
fun TomScreen(audioEngine: AudioEngine) {
    val state by audioEngine.state.collectAsState()

    // Animation Logic
    var currentImageRes by remember { mutableStateOf(R.drawable.thinking) }

    LaunchedEffect(state) {
        when (state) {
            TomState.TALKING -> {
                while (state == TomState.TALKING) {
                    currentImageRes = R.drawable.happy
                    delay(150)
                    currentImageRes = R.drawable.thinking
                    delay(150)
                }
            }
            TomState.LISTENING, TomState.IDLE -> {
                currentImageRes = R.drawable.thinking
            }
        }
    }

    // Start engine when UI is ready and permission granted
    LaunchedEffect(Unit) {
        audioEngine.start()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = currentImageRes),
            contentDescription = "Talking Tom",
            modifier = Modifier.fillMaxSize()
        )
    }
}

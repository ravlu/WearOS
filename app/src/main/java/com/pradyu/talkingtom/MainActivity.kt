package com.pradyu.talkingtom

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current

    // Load and split bitmap once
    val frames = remember {
        val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.spritesheet)
        splitBitmap(bitmap)
    }

    // Animation Logic
    var currentFrameIndex by remember { mutableStateOf(0) }

    LaunchedEffect(state) {
        if (state == TomState.TALKING) {
            while (true) {
                // Cycle between first two frames to simulate talking
                currentFrameIndex = (currentFrameIndex + 1) % 2
                delay(150)
            }
        } else {
            currentFrameIndex = 0 // Reset to Idle
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
        if (frames.isNotEmpty()) {
            Image(
                bitmap = frames[currentFrameIndex % frames.size],
                contentDescription = "Talking Tom",
                modifier = Modifier.fillMaxSize()
            )
        }

        // Optional debug indicator for listening state
        /*
        if (state == TomState.LISTENING) {
             Text(text = "👂", modifier = Modifier.align(Alignment.BottomCenter))
        }
        */
    }
}

fun splitBitmap(bitmap: Bitmap): List<ImageBitmap> {
    val w = bitmap.width / 2
    val h = bitmap.height / 2
    val list = mutableListOf<ImageBitmap>()
    // Order: TL, TR, BL, BR
    list.add(Bitmap.createBitmap(bitmap, 0, 0, w, h).asImageBitmap())
    list.add(Bitmap.createBitmap(bitmap, w, 0, w, h).asImageBitmap())
    list.add(Bitmap.createBitmap(bitmap, 0, h, w, h).asImageBitmap())
    list.add(Bitmap.createBitmap(bitmap, w, h, w, h).asImageBitmap())
    return list
}

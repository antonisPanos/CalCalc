package com.example.calcalc.camera

import android.Manifest
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Full-screen in-app capture. Using CameraX directly rather than an intent to the system
 * camera keeps the flow inside the chat — no app switch, no file provider plumbing.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraCaptureDialog(
    onDismiss: () -> Unit,
    onCaptured: (ByteArray) -> Unit,
) {
    val permission = rememberPermissionState(Manifest.permission.CAMERA)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (permission.status.isGranted) {
                CameraPreviewWithShutter(onDismiss = onDismiss, onCaptured = onCaptured)
            } else {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "CalCalc needs the camera to photograph your meal.",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Button(
                        onClick = { permission.launchPermissionRequest() },
                        modifier = Modifier.padding(top = 16.dp),
                    ) { Text("Allow camera") }
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        }
    }
}

@Composable
private fun CameraPreviewWithShutter(
    onDismiss: () -> Unit,
    onCaptured: (ByteArray) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().build() }
    var capturing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    LaunchedEffect(Unit) {
        val provider = ProcessCameraProvider.getInstance(context).await()
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        runCatching {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
        }.onFailure { error = it.message }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.TopCenter).padding(24.dp),
            )
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconButton(
                enabled = !capturing,
                onClick = {
                    capturing = true
                    imageCapture.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val bytes = image.toJpegBytes()
                                image.close()
                                capturing = false
                                onCaptured(bytes)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                capturing = false
                                error = exception.message ?: "Couldn't take the photo."
                            }
                        },
                    )
                },
                modifier = Modifier
                    .size(72.dp)
                    .background(Color.White.copy(alpha = if (capturing) 0.4f else 0.9f), CircleShape),
            ) {}
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White) }
        }
    }
}

/** [ImageCapture] hands back a single JPEG plane by default. */
private fun ImageProxy.toJpegBytes(): ByteArray {
    val buffer = planes[0].buffer
    return ByteArray(buffer.remaining()).also { buffer.get(it) }
}

private suspend fun <T> com.google.common.util.concurrent.ListenableFuture<T>.await(): T =
    suspendCoroutine { continuation ->
        addListener({ continuation.resume(get()) }, Runnable::run)
    }

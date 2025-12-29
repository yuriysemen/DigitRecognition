@file:OptIn(androidx.camera.core.ExperimentalGetImage::class)

package com.example.digitrecognition

import android.Manifest
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import decodeBitmapApplyingExif
import java.io.File
import java.io.FileInputStream
import java.util.Locale

@Composable
fun CameraCaptureScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )

    // UI state after capture
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var capturedBytes by remember { mutableStateOf<Long?>(null) }
    var headerHex by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val outputFile = remember { File(context.cacheDir, "capture.jpg") }

    // CameraX objects
    val previewView = remember { PreviewView(context) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    // Request permission on first render (or you can show a button)
    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Start camera when permission is granted
    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) return@LaunchedEffect

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val selector = CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, capture)

            imageCapture = capture
        }, executor)
    }

    fun takePhoto() {
        error = null

        val capture = imageCapture ?: run {
            error = "Camera is not ready yet."
            return
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
        val executor = ContextCompat.getMainExecutor(context)

        capture.takePicture(
            outputOptions,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    capturedBitmap = decodeBitmapApplyingExif(outputFile)
                    capturedBytes = outputFile.length()
                    headerHex = readFirstBytesAsHex(outputFile, count = 16)
                }

                override fun onError(exception: ImageCaptureException) {
                    error = "Capture failed: ${exception.message}"
                }
            }
        )
    }

    fun reset() {
        capturedBitmap = null
        capturedBytes = null
        headerHex = null
        error = null
        if (outputFile.exists()) outputFile.delete()
    }

    // UI (two sections)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section 1: result/status (here we show bytes; later you will show detected digit)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Found / Result", style = MaterialTheme.typography.titleMedium)

                val bytes = capturedBytes
                if (bytes != null) {
                    Text("Image size: $bytes bytes")
                    headerHex?.let { Text("First 16 bytes: $it") }
                } else {
                    Text("No image yet.")
                }

                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }

        // Section 2: camera preview OR captured image + buttons
        Card(Modifier.fillMaxWidth().weight(1f)) {
            Column(
                modifier = Modifier.padding(16.dp).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Photo", style = MaterialTheme.typography.titleMedium)

                Box(Modifier.fillMaxWidth().weight(1f)) {
                    when {
                        !hasCameraPermission -> {
                            Text("Camera permission is required.")
                        }
                        capturedBitmap == null -> {
                            AndroidView(
                                factory = { previewView },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        else -> {
                            Image(
                                bitmap = capturedBitmap!!.asImageBitmap(),
                                contentDescription = "Captured photo",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { takePhoto() },
                        modifier = Modifier.weight(1f),
                        enabled = hasCameraPermission
                    ) {
                        Text("Make a photo")
                    }

                    OutlinedButton(
                        onClick = { reset() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Try again")
                    }
                }
            }
        }
    }
}

private fun readFirstBytesAsHex(file: File, count: Int): String {
    FileInputStream(file).use { input ->
        val buffer = ByteArray(count)
        val read = input.read(buffer)
        val slice = if (read > 0) buffer.copyOf(read) else ByteArray(0)
        return slice.joinToString(" ") { b -> String.format(Locale.US, "%02X", b) }
    }
}

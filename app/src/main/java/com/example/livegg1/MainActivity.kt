package com.example.livegg1

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.livegg1.ui.theme.LiveGG1Theme
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape

class MainActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService
    // 使用一个 Map 来跟踪多个权限的状态
    private var permissionsGranted by mutableStateOf(mapOf<String, Boolean>())

    // 使用 RequestMultiplePermissions 来一次性请求所有需要的权限
    private val requestMultiplePermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissionsGranted = permissions
            permissions.entries.forEach {
                Log.d("MainActivity", "${it.key} = ${it.value}")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        // 定义我们 App 需要的所有权限
        val permissionsToRequest = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )

        // 检查并请求权限
        val notGrantedPermissions = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGrantedPermissions.isNotEmpty()) {
            Log.d("MainActivity", "Requesting permissions: $notGrantedPermissions")
            requestMultiplePermissionsLauncher.launch(notGrantedPermissions.toTypedArray())
        } else {
            Log.d("MainActivity", "All permissions already granted")
            // 如果所有权限都已授予，直接更新状态
            permissionsGranted = permissionsToRequest.associateWith { true }
        }


        setContent {
            LiveGG1Theme {
                // 检查所有需要的权限是否都已被授予
                val allPermissionsGranted = permissionsGranted.all { it.value }

                if (allPermissionsGranted) {
                    CameraScreen(cameraExecutor)
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Camera and/or Audio permission denied. Please grant permissions to use the app.")
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        Log.d("MainActivity", "CameraExecutor shut down")
    }
}

@Composable
fun CameraScreen(cameraExecutor: ExecutorService) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    val screenAspectRatio = configuration.screenWidthDp.toFloat() / configuration.screenHeightDp.toFloat()

    // --- 状态管理 ---
    var imageToShow by remember { mutableStateOf<Bitmap?>(null) }
    var captionToShow by remember { mutableStateOf("") }
    // 用于从 RecognitionListener 接收结果的状态
    var recognizedTextState by remember { mutableStateOf("") }

    // --- 相机设置 ---
    val imageCapture = remember { ImageCapture.Builder().build() }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    // --- 语音识别设置 ---
    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    val speechRecognizerIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            // putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
    }

    // --- 核心逻辑：生命周期管理 ---
    DisposableEffect(lifecycleOwner) {
        // 1. 设置相机
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val cameraProvider = cameraProviderFuture.get()
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)

        // 2. 设置一个稳定的 RecognitionListener
        val listener = object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val result = if (!matches.isNullOrEmpty()) matches[0] else "信息: 未匹配 (7)"
                Log.d("SpeechRecognizer", "Result: '$result'")
                recognizedTextState = result
            }

            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "错误: 音频 (1)"
                    SpeechRecognizer.ERROR_CLIENT -> "错误: 客户端 (5)"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "错误: 权限不足 (9)"
                    SpeechRecognizer.ERROR_NETWORK -> "错误: 网络 (2)"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "错误: 网络超时 (3)"
                    SpeechRecognizer.ERROR_NO_MATCH -> "信息: 未匹配 (7)"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "错误: 识别器繁忙 (8)"
                    SpeechRecognizer.ERROR_SERVER -> "错误: 服务器 (4)"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "信息: 语音超时 (6)"
                    else -> "错误: 未知 ($error)"
                }
                Log.e("SpeechRecognizer", errorMessage)
                recognizedTextState = errorMessage
            }

            override fun onReadyForSpeech(params: Bundle?) { Log.d("SpeechRecognizer", "onReadyForSpeech") }
            override fun onBeginningOfSpeech() { Log.d("SpeechRecognizer", "onBeginningOfSpeech") }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) { Log.d("SpeechRecognizer", "onBufferReceived") }
            override fun onEndOfSpeech() { Log.d("SpeechRecognizer", "onEndOfSpeech") }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
        speechRecognizer.setRecognitionListener(listener)

        // 3. 清理资源
        onDispose {
            cameraProvider.unbindAll()
            speechRecognizer.destroy()
        }
    }

    // --- 核心逻辑：定时拍照和识别的循环 ---
    LaunchedEffect(Unit) {
        // 首次启动时，先拍一张照片作为背景
        takePhoto(
            imageCapture = imageCapture,
            executor = cameraExecutor,
            onImageCaptured = { newBitmap ->
                val croppedBitmap = cropBitmapToAspectRatio(newBitmap, screenAspectRatio)
                imageToShow?.recycle()
                imageToShow = croppedBitmap
            },
            onError = { Log.e("MainLoop", "Initial photo capture failed", it) }
        )
        delay(1000) // 等待首次拍照完成

        while (true) {
            // 1. 重置状态并开始录音
            recognizedTextState = "" // 清空上一轮的结果
            speechRecognizer.startListening(speechRecognizerIntent)
            Log.d("MainLoop", "Started listening...")

            // 2. 等待5秒
            delay(5000L)
            
            // 3. 停止录音
            speechRecognizer.stopListening()
            Log.d("MainLoop", "Stopped listening.")

            // 4. 短暂等待，让 onResults/onError 回调有机会执行
            delay(1000L)

            // 5. 拍照并更新UI
            takePhoto(
                imageCapture = imageCapture,
                executor = cameraExecutor,
                onImageCaptured = { newBitmap ->
                    val croppedBitmap = cropBitmapToAspectRatio(newBitmap, screenAspectRatio)
                    imageToShow?.recycle()
                    imageToShow = croppedBitmap
                    captionToShow = recognizedTextState // 使用回调更新的状态
                    Log.d("MainLoop", "Photo and caption updated. New caption: '$captionToShow'")
                },
                onError = { Log.e("MainLoop", "Photo capture failed", it) }
            )

            // 6. 在下一次循环前等待，确保 SpeechRecognizer 完全重置
            delay(1000L)
        }
    }

    // --- UI 界面 (这部分保持不变) ---
    Box(modifier = Modifier.fillMaxSize()) {
        // 摄像头预览一直在底层运行，但用户看不见
        AndroidView({ previewView }, modifier = Modifier.fillMaxSize())

        // 如果有图片，就显示图片和字幕
        imageToShow?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Captured Image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (captionToShow.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = captionToShow,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        color = Color.White,
                        fontSize = 24.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

fun takePhoto(
    imageCapture: ImageCapture,
    executor: ExecutorService,
    onImageCaptured: (Bitmap) -> Unit,
    onError: (ImageCaptureException) -> Unit
) {
    imageCapture.takePicture(
        executor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                Log.d("takePhoto", "Capture success. Image format: ${imageProxy.format}")
                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                val buffer: ByteBuffer = imageProxy.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                imageProxy.close()

                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap != null) {
                    // Rotate the bitmap if necessary
                    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                    val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    onImageCaptured(rotatedBitmap)
                } else {
                    Log.e("takePhoto", "BitmapFactory.decodeByteArray returned null")
                    onError(
                        ImageCaptureException(
                            ImageCapture.ERROR_UNKNOWN,
                            "Failed to decode bitmap",
                            null
                        )
                    )
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(
                    "takePhoto",
                    "Photo capture error: ${exception.message} (code: ${exception.imageCaptureError})",
                    exception
                )
                onError(exception)
            }
        }
    )
}
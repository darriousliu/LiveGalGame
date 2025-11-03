package com.example.livegg1

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.livegg1.dialog.KeywordDialog
import com.example.livegg1.dialog.TriggerManagementDialog
import com.example.livegg1.ext.setImmersiveFullscreen
import com.example.livegg1.model.DialogType
import com.example.livegg1.model.KeywordTrigger
import com.example.livegg1.speech.KeywordSpeechListener
import com.example.livegg1.ui.CameraScreen
import com.example.livegg1.ui.theme.LiveGG1Theme
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private var permissionsGranted by mutableStateOf(mapOf<String, Boolean>())

    private val requestMultiplePermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissionsGranted = permissions
            permissions.entries.forEach {
                Log.d("MainActivity", "${it.key} = ${it.value}")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 设置全屏沉浸式模式
        setImmersiveFullscreen()

        cameraExecutor = Executors.newSingleThreadExecutor()

        val permissionsToRequest = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )

        val notGrantedPermissions = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGrantedPermissions.isNotEmpty()) {
            requestMultiplePermissionsLauncher.launch(notGrantedPermissions.toTypedArray())
        } else {
            permissionsGranted = permissionsToRequest.associateWith { true }
        }

        setContent {
            LiveGG1Theme {
                val allPermissionsGranted = permissionsGranted.all { it.value }
                if (allPermissionsGranted) {
                    val lifecycleOwner = LocalLifecycleOwner.current
                    var triggers by remember {
                        mutableStateOf(
                            listOf(
                                KeywordTrigger(
                                    keyword = "吗",
                                    dialogType = DialogType.CHOICE_DIALOG
                                )
                            )
                        )
                    }
                    val speechListener =
                        remember { KeywordSpeechListener(initialTriggers = triggers) }
                    var showKeywordDialog by remember { mutableStateOf(false) }
                    var showTriggerDialog by remember { mutableStateOf(false) }
                    var idleBgmAsset by remember { mutableStateOf("bgm.mp3") }
                    var activeTrigger by remember { mutableStateOf<KeywordTrigger?>(null) }
                    var affectionEventId by remember { mutableLongStateOf(0L) }
                    var affectionEventDelta by remember { mutableFloatStateOf(0f) }

                    fun queueAffectionChange(delta: Float) {
                        affectionEventDelta = delta
                        affectionEventId++
                    }

                    fun restartListeningIfPossible() {
                        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                            speechListener.startListening()
                        }
                    }

                    LaunchedEffect(triggers) {
                        speechListener.updateTriggers(triggers)
                    }

                    LaunchedEffect(showTriggerDialog) {
                        if (showTriggerDialog) {
                            speechListener.stopListening()
                        } else if (!showKeywordDialog) {
                            restartListeningIfPossible()
                        }
                    }

                    LaunchedEffect(Unit) {
                        speechListener.keywordTriggers.collect { trigger ->
                            Log.d("MainActivity", "Keyword triggered: ${trigger.keyword}")
                            speechListener.stopListening()
                            activeTrigger = trigger
                            showKeywordDialog = true
                        }
                    }

                    LifecycleStartEffect(Unit) {
                        speechListener.startListening()
                        onStopOrDispose {
                            if (lifecycle.currentState == Lifecycle.State.CREATED) {
                                speechListener.stopListening()
                            } else {
                                speechListener.release()
                            }
                        }
                    }

                    CameraScreen(
                        cameraExecutor = cameraExecutor,
                        onRecognizedText = { text, isFinal ->
                            speechListener.onRecognizedText(text, isFinal)
                        },
                        isDialogVisible = showKeywordDialog || showTriggerDialog,
                        idleBgmAsset = idleBgmAsset,
                        onManageTriggers = {
                            speechListener.stopListening()
                            showTriggerDialog = true
                        },
                        affectionEventId = affectionEventId,
                        affectionEventDelta = affectionEventDelta
                    )

                    if (showKeywordDialog) {
                        KeywordDialog(
                            onAccept = {
                                Log.d("MainActivity", "Keyword accepted: ${activeTrigger?.keyword}")
                                idleBgmAsset = "Ah.mp3"
                                queueAffectionChange(-0.4f)
                                showKeywordDialog = false
                                activeTrigger = null
                                if (!showTriggerDialog) {
                                    restartListeningIfPossible()
                                }
                            },
                            onReject = {
                                Log.d("MainActivity", "Keyword rejected: ${activeTrigger?.keyword}")
                                idleBgmAsset = "casual.mp3"
                                queueAffectionChange(0.4f)
                                showKeywordDialog = false
                                activeTrigger = null
                                if (!showTriggerDialog) {
                                    restartListeningIfPossible()
                                }
                            },
                            onDismiss = {
                                showKeywordDialog = false
                                activeTrigger = null
                                if (!showTriggerDialog) {
                                    restartListeningIfPossible()
                                }
                            },
                            onSelectBgm = { asset -> idleBgmAsset = asset }
                        )
                    }

                    if (showTriggerDialog) {
                        TriggerManagementDialog(
                            triggers = triggers,
                            onAddTrigger = { keyword, dialogType ->
                                val cleaned = keyword.trim()
                                val duplicate =
                                    triggers.any { it.keyword.equals(cleaned, ignoreCase = true) }
                                if (cleaned.isEmpty()) {
                                    Log.w("MainActivity", "Attempted to add empty keyword")
                                } else if (duplicate) {
                                    Log.w("MainActivity", "Keyword already exists: $cleaned")
                                } else {
                                    val newTrigger =
                                        KeywordTrigger(keyword = cleaned, dialogType = dialogType)
                                    Log.d("MainActivity", "Keyword added: ${newTrigger.keyword}")
                                    triggers = triggers + newTrigger
                                }
                            },
                            onUpdateTrigger = { original, updated ->
                                val cleaned = updated.keyword.trim()
                                val duplicate = triggers.any {
                                    it.id != original.id && it.keyword.equals(
                                        cleaned,
                                        ignoreCase = true
                                    )
                                }
                                if (cleaned.isEmpty()) {
                                    Log.w(
                                        "MainActivity",
                                        "Attempted to update keyword to empty value"
                                    )
                                } else if (duplicate) {
                                    Log.w("MainActivity", "Keyword already exists: $cleaned")
                                } else {
                                    val sanitized = updated.copy(keyword = cleaned)
                                    Log.d("MainActivity", "Keyword updated: ${sanitized.keyword}")
                                    triggers = triggers.map { existing ->
                                        if (existing.id == original.id) sanitized else existing
                                    }
                                }
                            },
                            onDeleteTrigger = { trigger ->
                                Log.d("MainActivity", "Keyword deleted: ${trigger.keyword}")
                                triggers = triggers.filterNot { it.id == trigger.id }
                            },
                            onDismiss = {
                                showTriggerDialog = false
                                if (!showKeywordDialog) {
                                    restartListeningIfPossible()
                                }
                            }
                        )
                    }
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
    }
}

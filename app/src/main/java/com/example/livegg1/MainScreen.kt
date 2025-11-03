package com.example.livegg1

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.livegg1.dialog.KeywordDialog
import com.example.livegg1.dialog.TriggerManagementDialog
import com.example.livegg1.ui.CameraScreen
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    var permissionsGranted by mutableStateOf(mapOf<String, Boolean>())
    val requestMultiplePermissionsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissionsGranted = permissions
            permissions.entries.forEach {
                Log.d("MainActivity", "${it.key} = ${it.value}")
            }
        }
    val allPermissionsGranted = permissionsGranted.all { it.value }
    LaunchedEffect(Unit) {
        val permissionsToRequest = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )

        val notGrantedPermissions = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGrantedPermissions.isNotEmpty()) {
            requestMultiplePermissionsLauncher.launch(notGrantedPermissions.toTypedArray())
        } else {
            permissionsGranted = permissionsToRequest.associateWith { true }
        }
    }
    if (allPermissionsGranted) {
        val lifecycleOwner = LocalLifecycleOwner.current
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) {
            viewModel.initSpeechListener()
            launch {
                viewModel.restartListeningFlow.collect {
                    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                        viewModel.startListening()
                    }
                }
            }
        }

        LifecycleStartEffect(Unit) {
            viewModel.startListening()
            onStopOrDispose {
                if (lifecycle.currentState == Lifecycle.State.CREATED) {
                    viewModel.stopListening()
                } else {
                    viewModel.release()
                }
            }
        }

        CameraScreen(
            cameraExecutor = viewModel.cameraExecutor,
            onRecognizedText = { text, isFinal ->
                viewModel.onRecognizedText(text, isFinal)
            },
            isDialogVisible = uiState.showKeywordDialog || uiState.showTriggerDialog,
            idleBgmAsset = uiState.idleBgmAsset,
            onManageTriggers = viewModel::onManageTriggers,
            affectionEventId = uiState.affectionEventId,
            affectionEventDelta = uiState.affectionEventDelta
        )

        if (uiState.showKeywordDialog) {
            KeywordDialog(
                onAccept = viewModel::keywordDialogOnAccept,
                onReject = viewModel::keywordDialogOnReject,
                onDismiss = viewModel::keywordDialogOnDismiss,
                onSelectBgm = viewModel::keywordDialogOnSelectBgm
            )
        }

        if (uiState.showTriggerDialog) {
            TriggerManagementDialog(
                triggers = uiState.triggers,
                onAddTrigger = viewModel::onAddTrigger,
                onUpdateTrigger = viewModel::onUpdateTrigger,
                onDeleteTrigger = viewModel::onDeleteTrigger,
                onDismiss = viewModel::onDismissTriggerDialog
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
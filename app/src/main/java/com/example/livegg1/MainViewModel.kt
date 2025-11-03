package com.example.livegg1

import android.util.Log
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.livegg1.model.DialogType
import com.example.livegg1.model.KeywordTrigger
import com.example.livegg1.speech.KeywordSpeechListener
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Stable
data class MainState(
    val triggers: List<KeywordTrigger> = listOf(
        KeywordTrigger(
            keyword = "吗",
            dialogType = DialogType.CHOICE_DIALOG
        )
    ),
    val showKeywordDialog: Boolean = false,
    var showTriggerDialog: Boolean = false,
    var idleBgmAsset: String = "bgm.mp3",
    var activeTrigger: KeywordTrigger? = null,
    var affectionEventId: Long = 0L,
    var affectionEventDelta: Float = 0f
)

class MainViewModel : ViewModel() {
    val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    val uiState = MutableStateFlow(MainState())

    val speechListener = KeywordSpeechListener(initialTriggers = uiState.value.triggers)
    val restartListeningFlow = MutableSharedFlow<Unit>()

    fun initSpeechListener() {
        speechListener.startListening()

        // 监听触发器变化并更新
        viewModelScope.launch {
            uiState.map { it.triggers }.collect { triggers ->
                speechListener.updateTriggers(triggers)
            }
        }

        // 收集关键词触发事件
        viewModelScope.launch {
            speechListener.keywordTriggers.collect { trigger ->
                Log.d("MainViewModel", "Keyword triggered: ${trigger.keyword}")
                speechListener.stopListening()
                uiState.update {
                    it.copy(
                        activeTrigger = trigger,
                        showKeywordDialog = true
                    )
                }
            }
        }
        combine(
            uiState.map { it.showTriggerDialog },
            uiState.map { it.showKeywordDialog },
        ) { showKeywordDialog, showTriggerDialog ->
            showKeywordDialog to showTriggerDialog
        }.onEach { (showKeyword, showTrigger) ->
            when {
                showTrigger -> speechListener.stopListening()
                !showKeyword && !showTrigger -> restartListeningIfPossible()
            }
        }.launchIn(viewModelScope)
    }

    fun queueAffectionChange(delta: Float) {
        uiState.update {
            it.copy(affectionEventDelta = delta, affectionEventId = it.affectionEventId + 1)
        }
    }

    fun startListening() {
        speechListener.startListening()
    }

    fun stopListening() {
        speechListener.stopListening()
    }

    fun release() {
        speechListener.release()
    }

    fun restartListeningIfPossible() {
        restartListeningFlow.tryEmit(Unit)
    }

    fun onRecognizedText(text: String, isFinal: Boolean) {
        speechListener.onRecognizedText(text, isFinal)
    }

    fun onManageTriggers() {
        speechListener.stopListening()
        uiState.update { it.copy(showTriggerDialog = true) }
    }

    fun keywordDialogOnAccept() {
        Log.d("MainActivity", "Keyword accepted: ${uiState.value.activeTrigger?.keyword}")
        uiState.update { it.copy(idleBgmAsset = "Ah.mp3") }
        queueAffectionChange(-0.4f)
        uiState.update { it.copy(showKeywordDialog = false, activeTrigger = null) }
        if (!uiState.value.showTriggerDialog) {
            restartListeningIfPossible()
        }
    }

    fun keywordDialogOnReject() {
        Log.d("MainActivity", "Keyword rejected: ${uiState.value.activeTrigger?.keyword}")
        uiState.update { it.copy(idleBgmAsset = "casual.mp3") }
        queueAffectionChange(0.4f)
        uiState.update { it.copy(showKeywordDialog = false, activeTrigger = null) }
        if (!uiState.value.showTriggerDialog) {
            restartListeningIfPossible()
        }
    }

    fun keywordDialogOnDismiss() {
        uiState.update { it.copy(showKeywordDialog = false, activeTrigger = null) }
        if (!uiState.value.showTriggerDialog) {
            restartListeningIfPossible()
        }
    }

    fun keywordDialogOnSelectBgm(bgmAsset: String) {
        uiState.update { it.copy(idleBgmAsset = bgmAsset) }
    }

    fun onAddTrigger(keyword: String, dialogType: DialogType) {
        val cleaned = keyword.trim()
        val duplicate = uiState.value.triggers.any { it.keyword.equals(cleaned, ignoreCase = true) }
        if (cleaned.isEmpty()) {
            Log.w("MainActivity", "Attempted to add empty keyword")
        } else if (duplicate) {
            Log.w("MainActivity", "Keyword already exists: $cleaned")
        } else {
            val newTrigger =
                KeywordTrigger(keyword = cleaned, dialogType = dialogType)
            Log.d("MainActivity", "Keyword added: ${newTrigger.keyword}")
            uiState.update { it.copy(triggers = it.triggers + newTrigger) }
        }
    }

    fun onUpdateTrigger(original: KeywordTrigger, updated: KeywordTrigger) {
        val cleaned = updated.keyword.trim()
        val duplicate = uiState.value.triggers.any {
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
            uiState.update {
                it.copy(triggers = it.triggers.map { existing ->
                    if (existing.id == original.id) sanitized else existing
                })
            }
        }
    }

    fun onDeleteTrigger(trigger: KeywordTrigger) {
        Log.d("MainActivity", "Keyword deleted: ${trigger.keyword}")
        uiState.update { it.copy(triggers = it.triggers.filterNot { it.id == trigger.id }) }
    }

    fun onDismissTriggerDialog() {
        uiState.update { it.copy(showTriggerDialog = false) }
        if (!uiState.value.showKeywordDialog) {
            restartListeningIfPossible()
        }
    }

    override fun onCleared() {
        speechListener.release()
        cameraExecutor.shutdown()
    }
}


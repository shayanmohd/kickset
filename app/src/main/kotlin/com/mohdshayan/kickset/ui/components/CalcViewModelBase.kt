package com.mohdshayan.kickset.ui.components

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mohdshayan.kickset.core.jobs.CalcKind
import com.mohdshayan.kickset.core.units.UnitSystem
import com.mohdshayan.kickset.data.db.JobSummary
import com.mohdshayan.kickset.data.db.SavedCalc
import com.mohdshayan.kickset.data.prefs.Settings
import com.mohdshayan.kickset.di.ServiceLocator
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/** A message for the snackbar, and whether the review prompt may follow it. */
data class UiMessage(val text: String, val mayPromptReview: Boolean = false)

/**
 * Shared calculator plumbing: inputs survive rotation and process death through SavedStateHandle, and an
 * app kill mid-edit through a DataStore draft; saving writes one Room transaction.
 */
@OptIn(FlowPreview::class)
abstract class CalcViewModelBase<I : Any>(
    app: Application,
    private val handle: SavedStateHandle,
    private val draftName: String,
    private val serializer: KSerializer<I>,
    default: I,
) : AndroidViewModel(app) {
    protected val prefs = ServiceLocator.appPrefs
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun decode(s: String?): I? = s?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }

    private val restored = decode(handle.get<String>(KEY))
    protected val inputsFlow = MutableStateFlow(restored ?: default)

    val settings: StateFlow<Settings?> = prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val jobs: StateFlow<List<JobSummary>> = ServiceLocator.jobDao.observeSummaries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val messageChannel = Channel<UiMessage>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    init {
        if (restored == null) viewModelScope.launch {
            val draft = decode(prefs.draft(draftName))
            if (draft != null && inputsFlow.value == default) inputsFlow.value = draft
        }
        viewModelScope.launch {
            inputsFlow.drop(1).debounce(250).collect { prefs.saveDraft(draftName, json.encodeToString(serializer, it)) }
        }
    }

    fun update(transform: (I) -> I) {
        val next = transform(inputsFlow.value)
        inputsFlow.value = next
        handle[KEY] = json.encodeToString(serializer, next)
    }

    protected fun replace(i: I) = update { i }

    fun inputsJson(): String = json.encodeToString(serializer, inputsFlow.value)

    fun save(jobId: Long?, newJobName: String?, label: String, kind: CalcKind, headline: String, unit: UnitSystem) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            try {
                ServiceLocator.database.saveCalc(jobId, newJobName, SavedCalc(0, 0, kind.name, label, inputsJson(), headline, unit.name, now), now)
                prefs.count("save")
                val prompt = prefs.recordSuccessAndShouldPrompt(now)
                messageChannel.send(UiMessage("Saved to $label.", prompt))
            } catch (e: Exception) {
                messageChannel.send(UiMessage("Could not save. Try again."))
            }
        }
    }

    protected fun send(m: UiMessage) { viewModelScope.launch { messageChannel.send(m) } }

    companion object { private const val KEY = "inputs" }
}

package io.github.wkoller.glucosetracker.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.wkoller.glucosetracker.AppContainer
import io.github.wkoller.glucosetracker.ai.AssistantContext
import io.github.wkoller.glucosetracker.ai.AssistantReply
import io.github.wkoller.glucosetracker.ai.ModelState
import io.github.wkoller.glucosetracker.domain.readout.ReadoutPeriod
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AnswerKind { MODEL, DOSE, URGENT, HYPO, CRISIS, FAILED }

data class Exchange(
    val question: String,
    val answer: String = "",
    val kind: AnswerKind = AnswerKind.MODEL,
    val loadingModel: Boolean = false,
    val streaming: Boolean = true,
)

data class AssistantUi(
    val context: AssistantContext? = null,
    val period: ReadoutPeriod = ReadoutPeriod.TODAY,
    val modelState: ModelState = ModelState.NotDownloaded,
    val enabled: Boolean = false,
    val summary: Exchange? = null,
    val exchanges: List<Exchange> = emptyList(),
    val question: String = "",
    /** The period chosen on the Progress screen, when the assistant was opened from there. */
    val progressDays: Int? = null,
) {
    val modelReady: Boolean get() = enabled && modelState == ModelState.Ready
    val busy: Boolean get() = summary?.streaming == true || exchanges.any { it.streaming }
}

class AssistantViewModel(private val container: AppContainer, progressDays: Int? = null) : ViewModel() {
    private val service = container.assistant
    private val _ui = MutableStateFlow(AssistantUi(progressDays = progressDays))
    val ui: StateFlow<AssistantUi> = _ui.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch { _ui.update { it.copy(context = service.context(progressDays)) } }
        viewModelScope.launch { container.models.state.collect { state -> _ui.update { it.copy(modelState = state) } } }
        viewModelScope.launch { container.settingsStore.settings.collect { s -> _ui.update { it.copy(enabled = s.assistantEnabled) } } }
        viewModelScope.launch { container.models.refresh() }
    }

    fun setPeriod(period: ReadoutPeriod) = _ui.update { it.copy(period = period, summary = null) }

    fun setQuestion(text: String) = _ui.update { it.copy(question = text) }

    fun summarise() {
        val context = _ui.value.context ?: return
        if (_ui.value.busy) return
        _ui.update { it.copy(summary = Exchange(question = "")) }
        job = viewModelScope.launch {
            val days = _ui.value.progressDays ?: _ui.value.period.days.toInt()
            service.summarise(context, days).collect { reply -> _ui.update { it.copy(summary = it.summary?.let { s -> apply(s, reply) }) } }
            _ui.update { it.copy(summary = it.summary?.copy(streaming = false, loadingModel = false)) }
        }
    }

    fun ask(text: String = _ui.value.question) {
        val question = text.trim()
        val context = _ui.value.context ?: return
        if (question.isEmpty() || _ui.value.busy) return
        val history = _ui.value.exchanges.filter { it.kind == AnswerKind.MODEL && it.answer.isNotBlank() }.map { it.question to it.answer }
        _ui.update { it.copy(question = "", exchanges = it.exchanges + Exchange(question)) }
        job = viewModelScope.launch {
            service.ask(context, question, history).collect { reply -> updateLast { apply(it, reply) } }
            updateLast { it.copy(streaming = false, loadingModel = false) }
        }
    }

    fun stop() {
        service.stop()
        job?.cancel()
        _ui.update { state ->
            state.copy(
                summary = state.summary?.copy(streaming = false, loadingModel = false),
                exchanges = state.exchanges.map { it.copy(streaming = false, loadingModel = false) },
            )
        }
    }

    private fun updateLast(transform: (Exchange) -> Exchange) = _ui.update { state ->
        if (state.exchanges.isEmpty()) state else state.copy(exchanges = state.exchanges.dropLast(1) + transform(state.exchanges.last()))
    }

    private fun apply(exchange: Exchange, reply: AssistantReply): Exchange = when (reply) {
        AssistantReply.Loading -> exchange.copy(loadingModel = true)
        is AssistantReply.Streaming -> exchange.copy(answer = reply.text, loadingModel = false)
        is AssistantReply.Done -> exchange.copy(answer = reply.text, streaming = false, loadingModel = false)
        AssistantReply.DoseQuestion -> exchange.copy(answer = "", kind = AnswerKind.DOSE, streaming = false, loadingModel = false)
        AssistantReply.Urgent -> exchange.copy(answer = "", kind = AnswerKind.URGENT, streaming = false, loadingModel = false)
        AssistantReply.Hypo -> exchange.copy(answer = "", kind = AnswerKind.HYPO, streaming = false, loadingModel = false)
        AssistantReply.Crisis -> exchange.copy(answer = "", kind = AnswerKind.CRISIS, streaming = false, loadingModel = false)
        AssistantReply.Failed -> exchange.copy(kind = AnswerKind.FAILED, streaming = false, loadingModel = false)
    }

    override fun onCleared() {
        service.stop()
    }
}

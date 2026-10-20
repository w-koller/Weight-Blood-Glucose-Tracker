package io.github.wkoller.glucosetracker.ai

import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import io.github.wkoller.glucosetracker.data.TrackerRepository
import io.github.wkoller.glucosetracker.domain.assistant.AssistantGuard
import io.github.wkoller.glucosetracker.domain.assistant.PromptBuilder
import io.github.wkoller.glucosetracker.domain.assistant.QuestionKind
import io.github.wkoller.glucosetracker.domain.model.DoseRecord
import io.github.wkoller.glucosetracker.domain.model.GlucoseReading
import io.github.wkoller.glucosetracker.domain.model.Plan
import io.github.wkoller.glucosetracker.domain.model.SlotNaming
import io.github.wkoller.glucosetracker.domain.progress.ProgressModel
import io.github.wkoller.glucosetracker.domain.progress.ProgressModelBuilder
import io.github.wkoller.glucosetracker.domain.readout.ReadoutBuilder
import io.github.wkoller.glucosetracker.domain.readout.ReadoutFacts
import io.github.wkoller.glucosetracker.domain.readout.ReadoutPeriod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.takeWhile
import java.time.LocalDate

/**
 * Everything the assistant knows about, gathered fresh for each request. [progress] is the period the
 * model is told about: the last 7 days, or the period chosen on the Progress screen when
 * [progressDays] is set.
 */
data class AssistantContext(
    val plan: Plan,
    val today: LocalDate,
    val todayFacts: ReadoutFacts,
    val weekFacts: ReadoutFacts,
    val progress: ProgressModel,
    val readings: List<GlucoseReading>,
    val doses: List<DoseRecord>,
    val firstName: String? = null,
    val slotNaming: SlotNaming = SlotNaming.MEALS,
    val progressDays: Int? = null,
) {
    fun facts(period: ReadoutPeriod) = if (period == ReadoutPeriod.TODAY) todayFacts else weekFacts
}

sealed interface AssistantReply {
    data object Loading : AssistantReply
    data class Streaming(val text: String) : AssistantReply
    data class Done(val text: String) : AssistantReply

    /** The question was about a dose, so the app answers it instead of the model. */
    data object DoseQuestion : AssistantReply

    /** The question mentioned an emergency, so the app answers it instead of the model. */
    data object Urgent : AssistantReply

    /** The question described signs of a low level, so the app says what to do instead of the model. */
    data object Hypo : AssistantReply

    /** The question mentioned suicide or self-harm, so the app gives numbers to call instead of the model. */
    data object Crisis : AssistantReply

    data object Failed : AssistantReply
}

/**
 * Talks to the on-device model. Dose questions and urgent symptoms never reach the model, and an
 * answer that mentions an insulin amount not found in the user's records is replaced by the app's
 * fixed dose answer.
 */
class AssistantService(
    private val repository: TrackerRepository,
    private val models: ModelManager,
    private val settings: io.github.wkoller.glucosetracker.data.prefs.SettingsStore,
) {
    @Volatile
    private var current: Conversation? = null

    /** [progressDays] is the period chosen on the Progress screen, or null for the last 7 days. */
    suspend fun context(progressDays: Int? = null): AssistantContext? {
        val plan = repository.currentPlan() ?: return null
        val today = repository.logicalToday(plan)
        val days = progressDays ?: ReadoutPeriod.WEEK.days.toInt()
        // Daily lines always cover the last 14 days, even when the period is shorter.
        val from = today.minusDays(maxOf(13L, days - 1L))
        val readings = repository.readingsBetween(from, today)
        val doses = repository.dosesBetween(from, today)
        val measurements = repository.allMeasurements()
        val current = settings.current()
        return AssistantContext(
            plan = plan,
            today = today,
            todayFacts = ReadoutBuilder.build(ReadoutPeriod.TODAY, plan, today, readings, doses, measurements),
            weekFacts = ReadoutBuilder.build(ReadoutPeriod.WEEK, plan, today, readings, doses, measurements),
            progress = ProgressModelBuilder.build(plan, today.minusDays(days - 1L), today, readings, doses, measurements),
            readings = readings,
            doses = doses,
            firstName = current.firstName.ifBlank { null },
            slotNaming = current.slotNaming,
            progressDays = progressDays,
        )
    }

    /** Summarises [days] days ending today: 1 for today, 7 for the week, or the Progress screen's period. */
    fun summarise(context: AssistantContext, days: Int): Flow<AssistantReply> =
        generate(context, PromptBuilder.summaryRequest(days), history = emptyList())

    fun ask(context: AssistantContext, question: String, history: List<Pair<String, String>>): Flow<AssistantReply> {
        val names = listOf(context.plan.correctionInsulin) + context.plan.medicines.map { it.name }
        return when (AssistantGuard.classify(question, names)) {
            QuestionKind.DOSE -> flowOfReply(AssistantReply.DoseQuestion)
            QuestionKind.URGENT -> flowOfReply(AssistantReply.Urgent)
            QuestionKind.HYPO -> flowOfReply(AssistantReply.Hypo)
            QuestionKind.CRISIS -> flowOfReply(AssistantReply.Crisis)
            QuestionKind.GENERAL -> generate(context, question, history)
        }
    }

    fun stop() {
        current?.cancelProcess()
    }

    private fun flowOfReply(reply: AssistantReply): Flow<AssistantReply> = channelFlow { send(reply) }

    private fun generate(context: AssistantContext, prompt: String, history: List<Pair<String, String>>): Flow<AssistantReply> = channelFlow {
        if (!models.isLoaded) send(AssistantReply.Loading)
        val system = PromptBuilder.systemPrompt(context.plan, context.firstName) + "\n\n" +
            PromptBuilder.factsBlock(context.plan, context.progress, context.today, context.readings, context.doses, context.slotNaming)
        val allowed = PromptBuilder.allowedAmounts(context.plan, context.doses, context.progress.doses)
        try {
            models.withEngine { engine ->
                val earlier = history.takeLast(MAX_HISTORY).flatMap { (q, a) -> listOf(Message.user(q), Message.model(a)) }
                val conversation = engine.createConversation(
                    ConversationConfig(Contents.of(system), earlier, emptyList(), SamplerConfig(TOP_K, TOP_P, TEMPERATURE, 0)),
                )
                current = conversation
                try {
                    var text = ""
                    var unsafe = false
                    conversation.sendMessageAsync(prompt)
                        .takeWhile { !unsafe }
                        .collect { message ->
                            val chunk = message.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
                            // Some versions send the whole reply so far, others only the new part.
                            text = if (text.isNotEmpty() && chunk.startsWith(text)) chunk else text + chunk
                            if (AssistantGuard.hasUnsupportedAmounts(text, allowed)) {
                                unsafe = true
                                conversation.cancelProcess()
                            } else {
                                send(AssistantReply.Streaming(text.trim()))
                            }
                        }
                    send(if (unsafe) AssistantReply.DoseQuestion else AssistantReply.Done(text.trim()))
                } finally {
                    current = null
                    conversation.close()
                }
            }
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            send(AssistantReply.Failed)
        }
    }.flowOn(Dispatchers.Default)

    private companion object {
        const val MAX_HISTORY = 2
        const val TOP_K = 40
        const val TOP_P = 0.95
        const val TEMPERATURE = 0.3
    }
}

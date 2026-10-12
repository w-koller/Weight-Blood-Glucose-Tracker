package io.github.wkoller.glucosetracker.tts

/** Which voice reads results aloud. Stored in settings as a short text value. */
sealed interface VoiceChoice {
    /** Whatever voice the phone is set to use. */
    data object PhoneDefault : VoiceChoice

    /** A particular voice installed on the phone, by its system name. */
    data class Phone(val name: String) : VoiceChoice

    /** One of the optional natural voices, by its speaker number in the voice model. */
    data class Natural(val speakerId: Int) : VoiceChoice

    fun encode(): String = when (this) {
        PhoneDefault -> ""
        is Phone -> "phone:$name"
        is Natural -> "natural:$speakerId"
    }

    companion object {
        fun decode(value: String): VoiceChoice = when {
            value.startsWith("phone:") -> value.removePrefix("phone:").takeIf { it.isNotBlank() }?.let(::Phone) ?: PhoneDefault
            value.startsWith("natural:") -> value.removePrefix("natural:").toIntOrNull()?.let(::Natural) ?: PhoneDefault
            else -> PhoneDefault
        }
    }
}

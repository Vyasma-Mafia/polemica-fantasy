package io.github.mralex1810.fantasy.telegram

data class InlineKeyboardMarkup(
    val inlineKeyboard: List<List<InlineKeyboardButton>>,
) {
    fun toMap(): Map<String, Any> = mapOf(
        "inline_keyboard" to inlineKeyboard.map { row -> row.map { it.toMap() } },
    )
}

sealed class InlineKeyboardButton {
    abstract val text: String

    abstract fun toMap(): Map<String, Any>

    data class WebApp(
        override val text: String,
        val url: String,
    ) : InlineKeyboardButton() {
        override fun toMap(): Map<String, Any> = mapOf(
            "text" to text,
            "web_app" to mapOf("url" to url),
        )
    }

    data class Callback(
        override val text: String,
        val callbackData: String,
    ) : InlineKeyboardButton() {
        override fun toMap(): Map<String, Any> = mapOf(
            "text" to text,
            "callback_data" to callbackData,
        )
    }

    data class Url(
        override val text: String,
        val url: String,
    ) : InlineKeyboardButton() {
        override fun toMap(): Map<String, Any> = mapOf(
            "text" to text,
            "url" to url,
        )
    }
}

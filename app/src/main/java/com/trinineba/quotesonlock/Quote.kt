package com.trinineba.quotesonlock

/** How the category toggles are grouped in settings. */
enum class QuoteGroup(val title: String) {
    THEME("Themes"),
    AUTHOR("Authors"),
    MINE("Yours"),
}

enum class Category(val key: String, val label: String, val group: QuoteGroup) {
    MOTIVATION("motivation", "Motivation", QuoteGroup.THEME),
    THOUGHT_PROVOKING("thought", "Thought-provoking", QuoteGroup.THEME),
    STOIC("stoic", "Stoic & ancient wisdom", QuoteGroup.THEME),
    KINDNESS("kindness", "Kindness & hope", QuoteGroup.THEME),
    GIBRAN("gibran", "Kahlil Gibran", QuoteGroup.AUTHOR),
    RILKE("rilke", "Rainer Maria Rilke", QuoteGroup.AUTHOR),
    NIETZSCHE("nietzsche", "Friedrich Nietzsche", QuoteGroup.AUTHOR),
    DOSTOEVSKY("dostoevsky", "Fyodor Dostoevsky", QuoteGroup.AUTHOR),
    TOLSTOY("tolstoy", "Leo Tolstoy", QuoteGroup.AUTHOR),
    KAFKA("kafka", "Franz Kafka", QuoteGroup.AUTHOR),
    PLATO("plato", "Plato", QuoteGroup.AUTHOR),
    LAO_TZU("laotzu", "Lao Tzu", QuoteGroup.AUTHOR),
    KIERKEGAARD("kierkegaard", "Søren Kierkegaard", QuoteGroup.AUTHOR),
    CUSTOM("custom", "My own quotes", QuoteGroup.MINE);

    companion object {
        fun fromKey(key: String): Category? = entries.firstOrNull { it.key == key }
    }
}

data class Quote(
    val text: String,
    val author: String?,
    /** Null for a quote restored from storage for display only. */
    val category: Category? = null,
    val id: String = "",
)

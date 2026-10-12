package com.trinineba.quotesonlock

enum class Category(val key: String, val label: String) {
    MOTIVATION("motivation", "Motivation"),
    THOUGHT_PROVOKING("thought", "Thought-provoking"),
    STOIC("stoic", "Stoic & ancient wisdom"),
    KINDNESS("kindness", "Kindness & hope"),
    CUSTOM("custom", "My own quotes");

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

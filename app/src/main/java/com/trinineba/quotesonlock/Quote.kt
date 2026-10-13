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
    CAMUS("camus", "Albert Camus", QuoteGroup.AUTHOR),
    HESSE("hesse", "Hermann Hesse", QuoteGroup.AUTHOR),
    CONFUCIUS("confucius", "Confucius", QuoteGroup.AUTHOR),
    DHAMMAPADA("dhammapada", "The Dhammapada", QuoteGroup.AUTHOR),
    RUMI("rumi", "Rumi", QuoteGroup.AUTHOR),
    KHAYYAM("khayyam", "Omar Khayyam", QuoteGroup.AUTHOR),
    SUN_TZU("suntzu", "Sun Tzu", QuoteGroup.AUTHOR),
    TAGORE("tagore", "Rabindranath Tagore", QuoteGroup.AUTHOR),
    EINSTEIN("einstein", "Albert Einstein", QuoteGroup.AUTHOR),
    MURAKAMI("murakami", "Haruki Murakami", QuoteGroup.AUTHOR),
    SHAKESPEARE("shakespeare", "William Shakespeare", QuoteGroup.AUTHOR),
    WHITMAN("whitman", "Walt Whitman", QuoteGroup.AUTHOR),
    DICKINSON("dickinson", "Emily Dickinson", QuoteGroup.AUTHOR),
    BLAKE("blake", "William Blake", QuoteGroup.AUTHOR),
    LEOPARDI("leopardi", "Giacomo Leopardi", QuoteGroup.AUTHOR),
    BAUDELAIRE("baudelaire", "Charles Baudelaire", QuoteGroup.AUTHOR),
    GITA("gita", "The Bhagavad Gita", QuoteGroup.AUTHOR),
    UPANISHADS("upanishads", "The Upanishads", QuoteGroup.AUTHOR),
    FRANKL("frankl", "Viktor Frankl", QuoteGroup.AUTHOR),
    PESSOA("pessoa", "Fernando Pessoa", QuoteGroup.AUTHOR),
    BORGES("borges", "Jorge Luis Borges", QuoteGroup.AUTHOR),
    CIORAN("cioran", "E. M. Cioran", QuoteGroup.AUTHOR),
    WEIL("weil", "Simone Weil", QuoteGroup.AUTHOR),
    NERUDA("neruda", "Pablo Neruda", QuoteGroup.AUTHOR),
    OLIVER("oliver", "Mary Oliver", QuoteGroup.AUTHOR),
    SZYMBORSKA("szymborska", "Wisława Szymborska", QuoteGroup.AUTHOR),
    KUNDERA("kundera", "Milan Kundera", QuoteGroup.AUTHOR),
    SARTRE("sartre", "Jean-Paul Sartre", QuoteGroup.AUTHOR),
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

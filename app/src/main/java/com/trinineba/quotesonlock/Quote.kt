package com.trinineba.quotesonlock

/** How the category toggles are grouped in settings. */
enum class QuoteGroup(val title: String) {
    THEME("Themes"),
    POETS("Poets"),
    THINKERS("Philosophers & sages"),
    WRITERS("Novelists & essayists"),
    SCRIPTURE("Scripture & ancient wisdom"),
    MINE("Yours"),
}

enum class Category(val key: String, val label: String, val group: QuoteGroup) {
    MOTIVATION("motivation", "Motivation", QuoteGroup.THEME),
    THOUGHT_PROVOKING("thought", "Thought-provoking", QuoteGroup.THEME),
    STOIC("stoic", "Stoic & ancient wisdom", QuoteGroup.THEME),
    KINDNESS("kindness", "Kindness & hope", QuoteGroup.THEME),
    GIBRAN("gibran", "Kahlil Gibran", QuoteGroup.POETS),
    RILKE("rilke", "Rainer Maria Rilke", QuoteGroup.POETS),
    NIETZSCHE("nietzsche", "Friedrich Nietzsche", QuoteGroup.THINKERS),
    DOSTOEVSKY("dostoevsky", "Fyodor Dostoevsky", QuoteGroup.WRITERS),
    TOLSTOY("tolstoy", "Leo Tolstoy", QuoteGroup.WRITERS),
    KAFKA("kafka", "Franz Kafka", QuoteGroup.WRITERS),
    PLATO("plato", "Plato", QuoteGroup.THINKERS),
    LAO_TZU("laotzu", "Lao Tzu", QuoteGroup.THINKERS),
    KIERKEGAARD("kierkegaard", "Søren Kierkegaard", QuoteGroup.THINKERS),
    CAMUS("camus", "Albert Camus", QuoteGroup.THINKERS),
    HESSE("hesse", "Hermann Hesse", QuoteGroup.WRITERS),
    CONFUCIUS("confucius", "Confucius", QuoteGroup.THINKERS),
    DHAMMAPADA("dhammapada", "The Dhammapada", QuoteGroup.SCRIPTURE),
    RUMI("rumi", "Rumi", QuoteGroup.POETS),
    KHAYYAM("khayyam", "Omar Khayyam", QuoteGroup.POETS),
    SUN_TZU("suntzu", "Sun Tzu", QuoteGroup.THINKERS),
    TAGORE("tagore", "Rabindranath Tagore", QuoteGroup.POETS),
    EINSTEIN("einstein", "Albert Einstein", QuoteGroup.THINKERS),
    MURAKAMI("murakami", "Haruki Murakami", QuoteGroup.WRITERS),
    SHAKESPEARE("shakespeare", "William Shakespeare", QuoteGroup.POETS),
    WHITMAN("whitman", "Walt Whitman", QuoteGroup.POETS),
    DICKINSON("dickinson", "Emily Dickinson", QuoteGroup.POETS),
    BLAKE("blake", "William Blake", QuoteGroup.POETS),
    LEOPARDI("leopardi", "Giacomo Leopardi", QuoteGroup.POETS),
    BAUDELAIRE("baudelaire", "Charles Baudelaire", QuoteGroup.POETS),
    GITA("gita", "The Bhagavad Gita", QuoteGroup.SCRIPTURE),
    UPANISHADS("upanishads", "The Upanishads", QuoteGroup.SCRIPTURE),
    FRANKL("frankl", "Viktor Frankl", QuoteGroup.THINKERS),
    PESSOA("pessoa", "Fernando Pessoa", QuoteGroup.WRITERS),
    BORGES("borges", "Jorge Luis Borges", QuoteGroup.WRITERS),
    CIORAN("cioran", "E. M. Cioran", QuoteGroup.THINKERS),
    WEIL("weil", "Simone Weil", QuoteGroup.THINKERS),
    NERUDA("neruda", "Pablo Neruda", QuoteGroup.POETS),
    OLIVER("oliver", "Mary Oliver", QuoteGroup.POETS),
    SZYMBORSKA("szymborska", "Wisława Szymborska", QuoteGroup.POETS),
    KUNDERA("kundera", "Milan Kundera", QuoteGroup.WRITERS),
    SARTRE("sartre", "Jean-Paul Sartre", QuoteGroup.THINKERS),
    KABIR("kabir", "Kabir", QuoteGroup.POETS),
    ECCLESIASTES("ecclesiastes", "Ecclesiastes & Job", QuoteGroup.SCRIPTURE),
    ZHUANGZI("zhuangzi", "Zhuangzi", QuoteGroup.THINKERS),
    MELVILLE("melville", "Herman Melville", QuoteGroup.WRITERS),
    TURGENEV("turgenev", "Ivan Turgenev", QuoteGroup.WRITERS),
    CHEKHOV("chekhov", "Anton Chekhov", QuoteGroup.WRITERS),
    QURAN("quran", "The Qur'an", QuoteGroup.SCRIPTURE),
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

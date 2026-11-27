package com.trinineba.quotesonlock

/**
 * The thinking behind search: what a typed query means and how well a piece of text answers it.
 *
 * - Words are matched one by one, in any order, as words: "war" finds "wars" and "warring" but not "warm".
 * - Word forms count: "forgiving" finds "forgive" and "forgiveness"; "worries" finds "worry".
 * - Filler is ignored: "quotes about letting go", "something for when I can't sleep".
 * - Meaning words widen the net a little: "fear" also finds "afraid" and "dread", ranked below the word itself.
 * - Small typos in names and words are forgiven ("Neitzsche" is read as "Nietzsche").
 * - Results are ranked: the exact phrase first, then every word, then most words, then related words.
 *
 * Pure Kotlin with no Android in it, so it is tested on its own (SmartSearchTest).
 */
object Smart {
    /** One meaningful word of the query: the forms that count as the word itself, and related words. */
    class Term(val word: String, val forms: List<String>, val related: List<String>)

    class Query(val raw: String, val phrase: String, val terms: List<Term>, val corrected: String?) {
        /** Strings worth highlighting in a result. */
        val highlights: List<String> get() = (listOf(phrase).filter { it.length >= 2 } + terms.flatMap { it.forms }).distinct().sortedByDescending { it.length }
        val isEmpty: Boolean get() = phrase.length < 2
    }

    private val fillers = listOf(
        "quotes about", "quote about", "quotes on", "quotes for", "quote for", "quotes of", "lines about", "lines on", "poems about", "poem about",
        "something about", "something for", "something on", "anything about", "things about", "words for", "words about", "words on",
        "for when i", "for when you", "when i'm", "when im", "when i am", "when i", "when you", "i feel", "i am", "i'm", "feeling", "help me", "help with",
        "show me", "how to", "what to do", "find me", "looking for",
    )

    private val stop = setOf(
        "a", "an", "the", "of", "to", "and", "or", "in", "on", "for", "about", "with", "my", "me", "i", "is", "it", "be", "by", "at", "as",
        "that", "this", "some", "any", "what", "when", "how", "who", "do", "does", "from", "into", "your", "you", "we", "our", "so", "are",
        "was", "were", "am", "its", "it's", "if", "but", "not", "no", "all", "very", "just", "quote", "quotes",
        "can't", "cant", "cannot", "don't", "dont", "won't", "need", "want", "get", "feel", "really", "over", "up", "out",
    )

    private val tiny = setOf("", "s", "es", "ing")

    /** Endings that may follow a short stem and still be the same word. */
    private val endings = setOf("", "s", "es", "ed", "d", "ing", "er", "ers", "est", "y", "ies", "ied", "ly", "ness", "ful", "e", "en", "ment", "ments", "ion", "ions")

    /**
     * Meaning words: a query word (by its stem) and words close to it. Kept small and plain on purpose; the subject
     * tags and the "close in meaning" shelf do the rest.
     */
    private val related: Map<String, List<String>> = mapOf(
        "sleep" to listOf("rest", "night", "insomnia", "slumber", "bed", "dream", "weary"),
        "insomnia" to listOf("sleep", "night", "rest", "restless"),
        "fear" to listOf("afraid", "dread", "terror", "frighten", "anxious", "anxiety", "courage"),
        "afraid" to listOf("fear", "dread", "frighten", "courage"),
        "worr" to listOf("anxious", "anxiety", "fret", "fear", "trouble"),
        "anxi" to listOf("worry", "fear", "calm", "peace", "nerves"),
        "stress" to listOf("pressure", "worry", "overwhelm", "burden", "calm"),
        "overwhelm" to listOf("burden", "too much", "rest", "breathe", "one thing"),
        "grief" to listOf("grieve", "loss", "mourn", "sorrow", "tears", "death", "missing"),
        "griev" to listOf("grief", "loss", "mourn", "sorrow", "tears"),
        "loss" to listOf("lost", "grief", "mourn", "sorrow", "gone"),
        "mourn" to listOf("grief", "loss", "sorrow", "tears"),
        "sad" to listOf("sorrow", "tears", "grief", "melancholy", "despair", "unhappy", "weep"),
        "depress" to listOf("despair", "darkness", "sorrow", "melancholy", "hope"),
        "lone" to listOf("alone", "solitude", "loneliness", "friend", "company"),
        "alone" to listOf("lonely", "solitude", "loneliness"),
        "anger" to listOf("angry", "rage", "wrath", "fury", "temper"),
        "angr" to listOf("anger", "rage", "wrath", "fury", "temper"),
        "hope" to listOf("hopeful", "despair", "light", "faith", "tomorrow"),
        "calm" to listOf("peace", "still", "quiet", "serene", "tranquil", "rest"),
        "peace" to listOf("calm", "still", "quiet", "serene", "tranquil"),
        "happ" to listOf("joy", "glad", "delight", "content", "cheer"),
        "joy" to listOf("happy", "happiness", "glad", "delight"),
        "love" to listOf("beloved", "heart", "lover", "loving"),
        "heartbreak" to listOf("broken heart", "love", "loss", "grief"),
        "death" to listOf("die", "dying", "dead", "mortal", "grave"),
        "die" to listOf("death", "dying", "dead", "mortal"),
        "start" to listOf("begin", "beginning", "new", "again", "first", "dawn", "renew"),
        "begin" to listOf("start", "new", "again", "first", "dawn"),
        "change" to listOf("transform", "new", "become", "different"),
        "courage" to listOf("brave", "bold", "fear", "dare", "valour"),
        "brave" to listOf("courage", "bold", "dare", "fear"),
        "fail" to listOf("failure", "defeat", "mistake", "fall", "error"),
        "mistake" to listOf("error", "fail", "failure", "wrong"),
        "success" to listOf("succeed", "achieve", "victory", "win"),
        "friend" to listOf("friendship", "companion", "kindness", "company"),
        "forgiv" to listOf("forgive", "forgiveness", "mercy", "pardon"),
        "patien" to listOf("patience", "wait", "slow", "endure"),
        "purpose" to listOf("meaning", "why", "calling"),
        "meaning" to listOf("purpose", "why", "meaningless"),
        "tired" to listOf("weary", "rest", "exhausted", "fatigue"),
        "exhaust" to listOf("weary", "tired", "rest", "fatigue"),
        "doubt" to listOf("uncertain", "believe", "certainty"),
        "confiden" to listOf("believe", "self", "doubt", "courage"),
        "work" to listOf("labour", "labor", "toil", "craft"),
        "money" to listOf("wealth", "rich", "poor", "gold", "riches"),
        "time" to listOf("hours", "years", "moment", "days"),
        "nature" to listOf("tree", "forest", "sea", "mountain", "river", "flower"),
        "home" to listOf("return", "house", "belong"),
        "family" to listOf("mother", "father", "child", "son", "daughter"),
        "mother" to listOf("mothers", "child", "family"),
        "father" to listOf("fathers", "son", "family"),
        "accept" to listOf("acceptance", "let go", "endure"),
        "kind" to listOf("kindness", "gentle", "compassion"),
        "compassion" to listOf("kindness", "mercy", "pity"),
        "silence" to listOf("silent", "quiet", "still"),
        "god" to listOf("divine", "lord", "heaven", "sacred"),
        "beauty" to listOf("beautiful", "lovely", "fair"),
    )

    /** A rough stem: enough to match word forms by prefix, never so short it matches everything. */
    fun stem(word: String): String {
        var w = word.lowercase()
        if (w.length <= 3) return w
        fun cut(suffix: String, keep: Int = 3): Boolean {
            if (w.endsWith(suffix) && w.length - suffix.length >= keep) { w = w.dropLast(suffix.length); return true }
            return false
        }
        when {
            cut("iness", 3) -> w += "y"
            cut("ness", 3) -> Unit
            cut("fully", 3) -> Unit
            cut("ful", 3) -> Unit
            cut("ies", 3) -> w += "y"
            cut("ied", 3) -> w += "y"
            cut("ing", 3) || cut("ed", 3) -> {
                // letting → let, stopped → stop
                if (w.length >= 3 && w[w.length - 1] == w[w.length - 2] && w.last() !in "lsz") w = w.dropLast(1)
            }
            cut("ly", 4) -> Unit
            cut("es", 4) -> Unit
            w.endsWith("s") && !w.endsWith("ss") && !w.endsWith("us") && !w.endsWith("is") -> w = w.dropLast(1)
        }
        // forgive → forgiv, so "forgiving" and "forgiveness" meet it
        if (w.length >= 5 && w.endsWith("e")) w = w.dropLast(1)
        return w
    }

    private val notWord = Regex("[^\\p{L}\\p{N}' ]+")
    private val spaces = Regex("\\s+")

    /** Lowercase words and single spaces, nothing else: the form every text is matched in. */
    fun normalise(s: String): String =
        s.lowercase().replace('’', '\'').replace('‘', '\'').replace(notWord, " ").replace(spaces, " ").trim()

    /** Understands a query. [vocabulary] (optional) is used to correct a word that appears nowhere. */
    fun parse(raw: String, vocabulary: Vocabulary? = null): Query {
        val phrase = normalise(raw)
        var body = " $phrase "
        for (f in fillers) body = body.replace(" $f ", " ")
        var words = body.trim().split(' ').filter { it.isNotEmpty() && it !in stop }
        var corrected: String? = null
        if (vocabulary != null) {
            val fixed = words.map { w -> if (w.length >= 5 && !vocabulary.knows(w)) vocabulary.closest(w) ?: w else w }
            if (fixed != words) { corrected = fixed.joinToString(" "); words = fixed }
        }
        // two-word meanings ("let go", "letting go", "can't sleep") travel together
        val joined = words.joinToString(" ")
        val terms = mutableListOf<Term>()
        val used = mutableSetOf<Int>()
        for ((key, rel) in related) {
            if (' ' !in key) continue
            val i = joined.indexOf(key)
            if (i >= 0) {
                val start = joined.substring(0, i).count { it == ' ' }
                key.split(' ').indices.forEach { used += start + it }
                terms += Term(key, listOf(key), rel)
            }
        }
        words.forEachIndexed { i, w ->
            if (i in used) return@forEachIndexed
            val st = stem(w)
            val forms = listOf(w, st).distinct()
            val rel = (related[st] ?: related[w] ?: related.entries.firstOrNull { (k, _) -> k.length >= 4 && st.startsWith(k) }?.value).orEmpty()
            terms += Term(w, forms, rel.filter { stem(it) != st })
        }
        return Query(raw, phrase, terms, corrected)
    }

    /** True when [form] occurs in [text] (already lowercase) as a word or the start of a word with a plain ending. */
    fun hasWord(text: String, form: String): Boolean {
        if (form.isEmpty()) return false
        var i = text.indexOf(form)
        while (i >= 0) {
            if (wordEnd(text, i, form) >= 0) return true
            i = text.indexOf(form, i + 1)
        }
        return false
    }

    /** Where the word starting at [i] ends, if [form] there counts as the word (see [hasWord]); -1 if it doesn't. */
    fun wordEnd(text: String, i: Int, form: String): Int {
        if (i > 0 && text[i - 1].isLetterOrDigit()) return -1
        var j = i + form.length
        while (j < text.length && text[j].isLetter()) j++
        val ending = text.substring(i + form.length, j)
        // tiny forms take only the plainest endings ("go" is not "god"); "d" only after an "e" ("love" → "loved");
        // longer forms may take any short ending
        val ok = when {
            form.contains(' ') -> ending.isEmpty() || ending in endings
            form.length <= 2 -> ending in tiny
            ending == "d" -> form.endsWith("e")
            else -> ending in endings || (form.length >= 5 && ending.length <= 5)
        }
        return if (ok) j else -1
    }

    /** True when [text] has one of the query's own words (not only a related one): for names and subjects. */
    fun direct(text: String?, q: Query): Boolean {
        if (text.isNullOrEmpty() || q.terms.isEmpty()) return false
        val t = normalise(text)
        return q.terms.any { term -> term.forms.any { hasWord(t, it) } }
    }

    /**
     * How well [text] answers [q]: 0 for not at all. The exact phrase scores highest, then each word found as itself,
     * then each found only through a related word. Every word must be found (with three or more, all but one).
     */
    fun score(text: String?, q: Query): Float = if (text.isNullOrEmpty()) 0f else scoreNormalised(normalise(text), q)

    /** [score], for text already put through [normalise] (kept once per quote, so typing stays quick). */
    fun scoreNormalised(t: String, q: Query): Float {
        if (t.isEmpty() || q.isEmpty) return 0f
        var s = 0f
        if (q.phrase.length >= 3 && t.contains(q.phrase)) s += 10f
        if (q.terms.isEmpty()) return s
        var missing = 0
        for (term in q.terms) {
            when {
                term.forms.any { hasWord(t, it) } -> s += 3f
                term.related.any { hasWord(t, it) } -> s += 1f
                else -> missing++
            }
        }
        val allowed = if (q.terms.size >= 3) 1 else 0
        if (missing > allowed) return if (s >= 10f) s else 0f
        return s - missing * 1.5f
    }

    /** About [width] characters around the first place any highlight appears in [text], cut at word boundaries. */
    fun snippet(text: String, q: Query, width: Int = 110): String? {
        val lower = text.lowercase()
        val hit = q.highlights.mapNotNull { h -> lower.indexOf(h).takeIf { it >= 0 }?.let { it to h.length } }.minByOrNull { it.first } ?: return null
        val (i, len) = hit
        var a = (i - width / 2).coerceAtLeast(0)
        var b = (i + len + width / 2).coerceAtMost(text.length)
        if (a > 0) a = text.indexOf(' ', a).let { if (it in a until i) it + 1 else a }
        if (b < text.length) b = text.lastIndexOf(' ', b).let { if (it > i + len) it else b }
        return (if (a > 0) "…" else "") + text.substring(a, b).trim() + if (b < text.length) "…" else ""
    }

    /** Damerau–Levenshtein distance, stopping early past [limit]. */
    fun distance(a: String, b: String, limit: Int = 3): Int {
        if (kotlin.math.abs(a.length - b.length) > limit) return limit + 1
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) {
            var rowMin = Int.MAX_VALUE
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
                rowMin = minOf(rowMin, d[i][j])
            }
            if (rowMin > limit) return limit + 1
        }
        return d[a.length][b.length]
    }

    /** The words the app knows (from every quote, title and name), for correcting typos. */
    class Vocabulary(texts: Sequence<String>) {
        private val counts = HashMap<String, Int>()
        init {
            texts.forEach { s -> normalise(s).split(' ').forEach { w -> if (w.length >= 3) counts[w] = (counts[w] ?: 0) + 1 } }
        }
        private val byStem: Set<String> = counts.keys.map { stem(it) }.toHashSet()

        fun knows(word: String): Boolean = word in counts || stem(word) in byStem

        /** The most common known word within one or two edits (two for long words), or null. */
        fun closest(word: String): String? {
            val limit = if (word.length >= 8) 2 else 1
            return counts.entries.asSequence()
                .filter { (w, _) -> kotlin.math.abs(w.length - word.length) <= limit && w[0] == word[0] }
                .mapNotNull { (w, n) -> distance(word, w, limit).takeIf { it <= limit }?.let { Triple(w, it, n) } }
                .sortedWith(compareBy<Triple<String, Int, Int>> { it.second }.thenByDescending { it.third })
                .firstOrNull()?.first
        }
    }
}

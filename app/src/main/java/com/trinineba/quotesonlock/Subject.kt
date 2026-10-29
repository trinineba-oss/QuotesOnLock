package com.trinineba.quotesonlock

/**
 * Browsing subjects. Each bundled quote carries up to three, picked by a keyword tagger when the
 * quote list is built (the fourth column of quotes.txt), so a subject cuts across every author.
 */
enum class Subject(val key: String, val label: String) {
    LOVE("love", "Love"),
    DEATH("death", "Death & mortality"),
    TIME("time", "Time & change"),
    SOLITUDE("solitude", "Solitude & silence"),
    NATURE("nature", "Nature & the seasons"),
    ART("art", "Art & beauty"),
    BOOKS("books", "Books, reading & writing"),
    SUFFERING("suffering", "Suffering & sorrow"),
    HOPE("hope", "Hope & consolation"),
    COURAGE("courage", "Courage & endurance"),
    FREEDOM("freedom", "Freedom & choice"),
    TRUTH("truth", "Truth & knowledge"),
    WISDOM("wisdom", "Wisdom & the good life"),
    SACRED("sacred", "God & the sacred"),
    JOY("joy", "Joy & happiness"),
    FRIENDSHIP("friendship", "Friendship & kindness"),
    MEMORY("memory", "Memory & the past"),
    SELF("self", "The self & identity"),
    MEANING("meaning", "Meaning & absurdity"),
    WORK("work", "Work, craft & discipline"),
    SOCIETY("society", "Power, society & war"),
    JOURNEY("journey", "Journeys & home"),
    DREAMS("dreams", "Dreams & imagination"),
    YOUTH("youth", "Youth & age");

    companion object {
        fun fromKey(key: String?): Subject? = entries.firstOrNull { it.key == key }
    }
}

package com.eddigits.eddido.model

/** The tabs, and what a piece of typed text can turn into. */
enum class Kind(val label: String, val jevHint: String) {
    TASK("Tasks", "Something to do or be reminded about, possibly at a date or time, including alarms and wake-ups"),
    TIMER("Timer", "Counting down a length of time right now, e.g. a 10 minute timer or 'timer 5 min for eggs'"),
    STOPWATCH("Stopwatch", "Starting a stopwatch or stop clock to measure how long something takes"),
    FOCUS("Focus", "A focus, study or deep work session or pomodoro, concentrating for a stretch with breaks"),
    HABIT("Habits", "A routine to build and track as a streak, e.g. drink water 8 times a day, meditate daily, gym 3 times a week"),
    LIST("Lists", "A shopping, grocery or packing list of several separate items to get"),
    COUNTDOWN("Countdown", "Counting the days until a future date or event, e.g. days until Diwali or a trip"),
    NOTE("Notes", "Writing down a thought, idea or piece of information to keep, not something to do"),
    ;

    val key: String get() = name.lowercase()

    companion object {
        fun fromKey(k: String?): Kind? = entries.firstOrNull { it.key == k?.lowercase()?.trim() }
    }
}

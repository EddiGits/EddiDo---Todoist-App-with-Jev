package com.eddigits.eddido.parse

/**
 * Numbers written as words, in English, Tamil and Hindi (in English letters), so
 * "two seconds", "twenty five minutes", "rendu nimisham" and "das minute" all read as numbers.
 */
object NumberWords {
    private val units = mapOf(
        // English
        "zero" to 0, "a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12,
        "thirteen" to 13, "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17,
        "eighteen" to 18, "nineteen" to 19,
        // Tamil
        "onnu" to 1, "ondru" to 1, "oru" to 1, "rendu" to 2, "irandu" to 2, "moonu" to 3, "munu" to 3,
        "naalu" to 4, "nalu" to 4, "anju" to 5, "ainthu" to 5, "aaru" to 6, "ezhu" to 7, "ettu" to 8,
        "onbadhu" to 9, "onbathu" to 9, "paththu" to 10, "pathu" to 10, "irupathu" to 20, "muppathu" to 30,
        "muppadhu" to 30, "narpathu" to 40, "aimbathu" to 50,
        // Hindi
        "ek" to 1, "do" to 2, "teen" to 3, "char" to 4, "chaar" to 4, "paanch" to 5, "panch" to 5,
        "chhe" to 6, "che" to 6, "saat" to 7, "aath" to 8, "nau" to 9, "das" to 10, "bees" to 20,
        "tees" to 30, "chalis" to 40, "pachas" to 50,
    )
    private val tens = mapOf(
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60,
        "seventy" to 70, "eighty" to 80, "ninety" to 90,
    )

    /** Regex alternative matching one number: digits, "twenty five", "twenty-five", "half an", "rendu"… */
    val PATTERN: String = run {
        val t = tens.keys.joinToString("|")
        val u = units.keys.sortedByDescending { it.length }.joinToString("|")
        "\\d+(?:\\.\\d+)?|half\\s+an?|(?:$t)(?:[\\s-]+(?:one|two|three|four|five|six|seven|eight|nine))?|$u"
    }

    /** The value of a match of [PATTERN], or null. */
    fun value(s: String): Double? {
        val w = s.lowercase().trim()
        w.toDoubleOrNull()?.let { return it }
        if (w.startsWith("half")) return 0.5
        val parts = w.split(Regex("[\\s-]+"))
        if (parts.size == 2 && parts[0] in tens) return (tens.getValue(parts[0]) + (units[parts[1]] ?: return null)).toDouble()
        return (tens[w] ?: units[w])?.toDouble()
    }
}

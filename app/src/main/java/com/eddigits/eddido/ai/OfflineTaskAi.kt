package com.eddigits.eddido.ai

import java.time.LocalDateTime

/**
 * Keyword categoriser that works with no network, like Shapeshift's "jev-offline".
 * Used when OpenRouter is unreachable or no key is set.
 */
class OfflineTaskAi : TaskAi {
    private val rules: List<Pair<String, Regex>> = listOf(
        "Shopping" to "buy|purchase|order|grocer|shopping|milk|eggs|bread|vegetables|amazon|flipkart|pick up",
        "Health" to "doctor|dentist|medicine|tablet|pill|gym|workout|exercise|run|walk|yoga|water|hospital|checkup|vitamin",
        "Finance" to "pay|bill|rent|emi|loan|bank|tax|invoice|salary|transfer|upi|credit card|insurance|recharge",
        "Work" to "meeting|client|report|deadline|office|email|presentation|project|review|standup|call with|boss|deploy|code|pr\\b",
        "Study" to "study|exam|homework|assignment|revise|read chapter|course|class|lecture|learn|practice",
        "Home" to "clean|laundry|wash|cook|dishes|repair|plumber|electrician|garden|water plants|trash|garbage",
        "Personal" to "birthday|anniversary|call mom|call dad|family|friend|party|movie|trip|travel|book tickets|gift",
    ).map { (p, words) -> p to Regex("(?i)\\b(?:$words)") }

    override suspend fun analyze(text: String, projects: List<String>, now: LocalDateTime): AiResult {
        val project = rules.firstOrNull { it.second.containsMatchIn(text) }?.first
        return AiResult(
            title = null,
            project = project,
            labels = emptyList(),
            priority = null,
            reminder = null,
            due = null,
            hasTime = false,
            recurrence = null,
            provider = "offline",
        )
    }
}

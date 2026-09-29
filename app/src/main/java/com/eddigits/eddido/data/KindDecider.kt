package com.eddigits.eddido.data

import com.eddigits.eddido.model.Kind

/**
 * Turns a flickery stream of "which tab?" answers into calm tab changes, ported from
 * Shapeshift's decide.ts:
 * - below 40% confidence nothing changes; 40–70% the tab is a "ghost" (tentative);
 *   70%+ it commits;
 * - once committed, a different tab must win twice in a row (or be 85%+ sure) to take over;
 * - a tab you tap yourself stays until the text changes substantially.
 */
object KindDecider {
    const val INPUT_BELOW = 0.4
    const val COMMIT_AT = 0.7
    const val CHALLENGER_OVERRIDE = 0.85
    const val CHALLENGER_WINS = 2
    const val DROP_BELOW = 0.3
    const val FORCED_CHANGE_RATIO = 0.3

    data class State(
        val kind: Kind = Kind.TASK,
        /** True while the tab is only a guess (shown lighter). */
        val ghost: Boolean = false,
        val forced: Boolean = false,
        val forcedText: String? = null,
        val challenger: Kind? = null,
        val challengerWins: Int = 0,
        /** Runner-up tabs worth offering as chips, best first. */
        val alternatives: List<Kind> = emptyList(),
    )

    fun decide(prev: State, probabilities: Map<Kind, Double>, text: String): State {
        if (text.isBlank()) return State(kind = prev.kind)
        if (prev.forced && prev.forcedText != null && !changedSubstantially(prev.forcedText, text)) return prev

        val ranked = probabilities.entries.sortedByDescending { it.value }
        if (ranked.isEmpty()) return prev
        val (top, conf) = ranked.first().let { it.key to it.value }
        val alternatives = ranked.drop(1).filter { it.value >= 0.15 }.map { it.key }.take(2)

        // Committed on some tab already.
        if (!prev.ghost && !prev.forced && prev.kind != top) {
            val currentP = probabilities[prev.kind] ?: 0.0
            if (conf >= CHALLENGER_OVERRIDE) return State(top, alternatives = alternatives)
            val wins = if (prev.challenger == top) prev.challengerWins + 1 else 1
            if (wins >= CHALLENGER_WINS && conf >= INPUT_BELOW) return State(top, ghost = conf < COMMIT_AT, alternatives = alternatives)
            if (currentP < DROP_BELOW && conf < INPUT_BELOW) return State(prev.kind, ghost = true, alternatives = alternatives)
            return prev.copy(challenger = top, challengerWins = wins, alternatives = alternatives, forced = false, forcedText = null)
        }
        return when {
            conf < INPUT_BELOW -> prev.copy(ghost = true, alternatives = listOf(top) + alternatives, challenger = null, challengerWins = 0, forced = false, forcedText = null)
            conf < COMMIT_AT -> State(top, ghost = true, alternatives = alternatives)
            else -> State(top, alternatives = alternatives)
        }
    }

    /** You tapped a tab: it stays until the text changes substantially. */
    fun force(kind: Kind, text: String) = State(kind, forced = true, forcedText = text)

    fun changedSubstantially(from: String, to: String): Boolean {
        val len = maxOf(from.length, to.length, 1)
        return levenshtein(from, to) > FORCED_CHANGE_RATIO * len
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return prev[b.length]
    }
}

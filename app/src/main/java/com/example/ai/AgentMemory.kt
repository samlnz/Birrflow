package com.example.ai

import com.example.data.repository.FinancialRepository
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

enum class MemoryKind(val label: String) {
    FACT("Fact"),
    PREFERENCE("Preference"),
    GOAL("Goal"),
    CONSTRAINT("Constraint"),
    LESSON("Lesson learned")
}

enum class MemorySource { USER, AGENT, SYSTEM }

data class MemoryItem(
    val id: String,
    val text: String,
    val kind: MemoryKind,
    val source: MemorySource,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val pinned: Boolean = false
)

/** What the advisor remembers between conversations, plus a running summary and answer feedback. */
data class AgentMemoryState(
    val items: List<MemoryItem> = emptyList(),
    // Rolling summary of older chat turns (the chat itself only sends the latest messages)
    val conversationSummary: String = "",
    // Timestamp of the newest chat message already folded into the summary
    val summarizedUpTo: Long = 0L,
    val helpfulCount: Int = 0,
    val unhelpfulCount: Int = 0
)

/**
 * Long-term memory for the AI advisor: durable facts, preferences, goals, constraints and lessons
 * (from the agent's remember tool, the user, answer feedback and conversation summaries). The most
 * relevant items are put in every system instruction, so the agent keeps learning about the user.
 */
object AgentMemoryService {

    private const val KEY_MEMORY = "AGENT_MEMORY"
    private const val MAX_ITEMS = 200
    // The chat sends the latest RECENT_TURNS messages; older ones are summarized in batches
    const val RECENT_TURNS = 12
    const val SUMMARY_BATCH = 6

    /** Messages that fell out of the recent window and are not in the summary yet. */
    fun pendingForSummary(state: AgentMemoryState, messages: List<AdvisorChatMessage>): List<AdvisorChatMessage> =
        messages.dropLast(RECENT_TURNS).filter { it.timestamp > state.summarizedUpTo && !it.message.startsWith("⚠️") }

    suspend fun load(repository: FinancialRepository): AgentMemoryState {
        val raw = repository.getState(KEY_MEMORY) ?: return AgentMemoryState()
        return try {
            val o = JSONObject(raw)
            val arr = o.optJSONArray("items") ?: JSONArray()
            AgentMemoryState(
                items = (0 until arr.length()).mapNotNull { i ->
                    val m = arr.optJSONObject(i) ?: return@mapNotNull null
                    MemoryItem(
                        id = m.optString("id", UUID.randomUUID().toString()),
                        text = m.optString("text"),
                        kind = try { MemoryKind.valueOf(m.optString("kind", "FACT")) } catch (_: Exception) { MemoryKind.FACT },
                        source = try { MemorySource.valueOf(m.optString("source", "AGENT")) } catch (_: Exception) { MemorySource.AGENT },
                        createdAt = m.optLong("createdAt", 0L),
                        updatedAt = m.optLong("updatedAt", 0L),
                        pinned = m.optBoolean("pinned", false)
                    )
                }.filter { it.text.isNotBlank() },
                conversationSummary = o.optString("conversationSummary", ""),
                summarizedUpTo = o.optLong("summarizedUpTo", 0L),
                helpfulCount = o.optInt("helpfulCount", 0),
                unhelpfulCount = o.optInt("unhelpfulCount", 0)
            )
        } catch (_: Exception) {
            AgentMemoryState()
        }
    }

    suspend fun save(repository: FinancialRepository, state: AgentMemoryState) {
        val arr = JSONArray()
        state.items.forEach { m ->
            arr.put(JSONObject().apply {
                put("id", m.id)
                put("text", m.text)
                put("kind", m.kind.name)
                put("source", m.source.name)
                put("createdAt", m.createdAt)
                put("updatedAt", m.updatedAt)
                put("pinned", m.pinned)
            })
        }
        val o = JSONObject()
            .put("items", arr)
            .put("conversationSummary", state.conversationSummary)
            .put("summarizedUpTo", state.summarizedUpTo)
            .put("helpfulCount", state.helpfulCount)
            .put("unhelpfulCount", state.unhelpfulCount)
        repository.putState(KEY_MEMORY, o.toString())
    }

    private fun normalize(text: String) = text.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()

    /** Adds a memory, or refreshes an existing one that says the same thing. */
    fun remember(state: AgentMemoryState, text: String, kind: MemoryKind, source: MemorySource, nowMillis: Long = System.currentTimeMillis()): Pair<AgentMemoryState, MemoryItem> {
        val clean = text.trim().take(400)
        val key = normalize(clean)
        val existing = state.items.firstOrNull { val other = normalize(it.text); other == key || (key.length > 20 && (other.contains(key) || key.contains(other))) }
        if (existing != null) {
            val updated = existing.copy(text = if (clean.length >= existing.text.length) clean else existing.text, kind = kind, updatedAt = nowMillis)
            return state.copy(items = state.items.map { if (it.id == existing.id) updated else it }) to updated
        }
        val item = MemoryItem(UUID.randomUUID().toString().take(8), clean, kind, source, nowMillis, nowMillis)
        var items = state.items + item
        if (items.size > MAX_ITEMS) {
            // Drop the oldest unpinned, non-lesson memories first
            val removable = items.filter { !it.pinned && it.kind != MemoryKind.LESSON }.sortedBy { it.updatedAt }
            val drop = removable.take(items.size - MAX_ITEMS).map { it.id }.toSet()
            items = items.filter { it.id !in drop }
        }
        return state.copy(items = items) to item
    }

    fun forget(state: AgentMemoryState, id: String): AgentMemoryState = state.copy(items = state.items.filter { it.id != id })

    fun togglePin(state: AgentMemoryState, id: String): AgentMemoryState =
        state.copy(items = state.items.map { if (it.id == id) it.copy(pinned = !it.pinned) else it })

    /** 👍/👎 on an answer. A 👎 with a reason becomes a lesson the agent sees from then on. */
    fun recordFeedback(state: AgentMemoryState, helpful: Boolean, question: String, reason: String): AgentMemoryState {
        val counted = if (helpful) state.copy(helpfulCount = state.helpfulCount + 1) else state.copy(unhelpfulCount = state.unhelpfulCount + 1)
        if (helpful || reason.isBlank()) return counted
        val lesson = "When asked \"${question.take(120)}\", the user found the answer unhelpful: ${reason.take(200)}"
        return remember(counted, lesson, MemoryKind.LESSON, MemorySource.USER).first
    }

    /** Pinned memories, then the most relevant to [query], then the most recent; up to [max]. */
    fun selectForPrompt(state: AgentMemoryState, query: String, max: Int = 30): List<MemoryItem> {
        val queryWords = CategoryLearner.tokens(query)
        fun relevance(item: MemoryItem): Int = CategoryLearner.tokens(item.text).count { it in queryWords }
        val pinned = state.items.filter { it.pinned }
        val lessons = state.items.filter { !it.pinned && it.kind == MemoryKind.LESSON }.sortedByDescending { it.updatedAt }.take(8)
        val rest = state.items.filter { !it.pinned && it.kind != MemoryKind.LESSON }
            .sortedWith(compareByDescending<MemoryItem> { relevance(it) }.thenByDescending { it.updatedAt })
        return (pinned + lessons + rest).distinctBy { it.id }.take(max)
    }

    /** Facts block for the advisor's system instruction. */
    fun toPromptSection(state: AgentMemoryState, query: String): String = buildString {
        appendLine("AGENT MEMORY (what you have learned about this user in earlier conversations; ids in brackets):")
        val selected = selectForPrompt(state, query)
        if (selected.isEmpty()) appendLine("• Nothing remembered yet.")
        selected.forEach { appendLine("• [${it.id}] ${it.kind.label}: ${it.text}") }
        val hidden = state.items.size - selected.size
        if (hidden > 0) appendLine("• ($hidden older memories not shown; use recall_memory to search them)")
        if (state.conversationSummary.isNotBlank()) appendLine("• Earlier conversation summary: ${state.conversationSummary}")
        if (state.helpfulCount + state.unhelpfulCount > 0) {
            appendLine("• Answer feedback so far: ${state.helpfulCount} helpful, ${state.unhelpfulCount} not helpful — avoid what the lessons above warn about.")
        }
        appendLine("• Save new durable facts, preferences, goals and constraints with remember_memory; correct wrong ones with forget_memory. Don't re-ask what you already know.")
    }.trimEnd()

    fun search(state: AgentMemoryState, query: String): List<MemoryItem> {
        val words = CategoryLearner.tokens(query)
        if (words.isEmpty()) return state.items.sortedByDescending { it.updatedAt }.take(20)
        return state.items
            .map { it to CategoryLearner.tokens(it.text).count { w -> w in words } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(20)
    }
}

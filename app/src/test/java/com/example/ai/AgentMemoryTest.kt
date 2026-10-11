package com.example.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentMemoryTest {

    @Test
    fun deduplicatesAndSelectsRelevantMemories() {
        var state = AgentMemoryState()
        state = AgentMemoryService.remember(state, "User sends 3,000 ETB to mother monthly", MemoryKind.FACT, MemorySource.AGENT).first
        state = AgentMemoryService.remember(state, "user sends 3,000 ETB to mother monthly.", MemoryKind.FACT, MemorySource.AGENT).first
        state = AgentMemoryService.remember(state, "Prefers interest-free products", MemoryKind.PREFERENCE, MemorySource.USER).first
        state = AgentMemoryService.remember(state, "Owns a small retail shop in Adama", MemoryKind.FACT, MemorySource.AGENT).first
        assertEquals(3, state.items.size)

        val selected = AgentMemoryService.selectForPrompt(state, "how should I restock my retail shop", max = 1)
        assertEquals("Owns a small retail shop in Adama", selected.single().text)

        val pinnedId = state.items.first { it.kind == MemoryKind.PREFERENCE }.id
        state = AgentMemoryService.togglePin(state, pinnedId)
        assertEquals(pinnedId, AgentMemoryService.selectForPrompt(state, "retail shop", max = 1).single().id)
    }

    @Test
    fun negativeFeedbackBecomesALesson() {
        val state = AgentMemoryService.recordFeedback(AgentMemoryState(), helpful = false, question = "Where should I invest 50,000?", reason = "ignored that I need the money in 2 months")
        assertEquals(1, state.unhelpfulCount)
        val lesson = state.items.single()
        assertEquals(MemoryKind.LESSON, lesson.kind)
        assertTrue(AgentMemoryService.toPromptSection(state, "invest").contains("ignored that I need the money"))
    }

    @Test
    fun onlyMessagesOutsideTheRecentWindowAreSummarized() {
        val messages = (1..20).map { AdvisorChatMessage("m$it", isUser = it % 2 == 1, message = "message $it", timestamp = it.toLong()) }
        val pending = AgentMemoryService.pendingForSummary(AgentMemoryState(summarizedUpTo = 3L), messages)
        assertEquals((4..8).map { "m$it" }, pending.map { it.id })
    }
}

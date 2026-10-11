package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.MemoryItem
import com.example.ai.MemoryKind
import com.example.ai.MemorySource
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.RoseExpense
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What the AI advisor remembers about the user; everything here can be edited or deleted. */
@Composable
fun AgentMemoryTab(viewModel: MainViewModel) {
    val memory by viewModel.agentMemory.collectAsStateWithLifecycle()
    var newText by remember { mutableStateOf("") }
    var newKind by remember { mutableStateOf(MemoryKind.FACT) }
    var confirmClear by remember { mutableStateOf(false) }
    val grouped = remember(memory) {
        memory.items.sortedWith(compareByDescending<MemoryItem> { it.pinned }.thenByDescending { it.updatedAt }).groupBy { it.kind }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("agent_memory_tab"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("What the advisor remembers", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "The advisor saves facts, preferences, goals, constraints and lessons from your chats and feedback, and reads the most relevant ones before every answer. Pin what matters most; delete anything wrong.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${memory.items.size} notes · answers rated 👍 ${memory.helpfulCount} / 👎 ${memory.unhelpfulCount}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Teach the advisor something", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    OutlinedTextField(
                        value = newText,
                        onValueChange = { newText = it.take(400) },
                        placeholder = { Text("e.g. I pay 3,000 ETB to my mother on the 1st of every month") },
                        modifier = Modifier.fillMaxWidth().testTag("memory_input"),
                        minLines = 2
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MemoryKind.values().forEach { kind ->
                            FilterChip(selected = newKind == kind, onClick = { newKind = kind }, label = { Text(kind.label, fontSize = 11.sp) })
                        }
                    }
                    Button(
                        onClick = {
                            viewModel.addMemory(newText.trim(), newKind)
                            newText = ""
                        },
                        enabled = newText.trim().length >= 5,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                    ) { Text("Remember this") }
                }
            }
        }

        if (memory.conversationSummary.isNotBlank()) {
            item {
                Column {
                    SectionLabel("EARLIER CONVERSATIONS (SUMMARY)")
                    Text(memory.conversationSummary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        if (memory.items.isEmpty()) {
            item { Hint("Nothing remembered yet. Chat with the advisor, rate its answers, or add notes above.") }
        }

        MemoryKind.values().forEach { kind ->
            val notes = grouped[kind].orEmpty()
            if (notes.isNotEmpty()) {
                item(key = "label_${kind.name}") { SectionLabel("${kind.label.uppercase()}S (${notes.size})") }
                items(notes, key = { it.id }) { note ->
                    MemoryRow(
                        note = note,
                        onPin = { viewModel.togglePinMemory(note.id) },
                        onDelete = { viewModel.forgetMemory(note.id) }
                    )
                }
            }
        }

        if (memory.items.isNotEmpty()) {
            item {
                TextButton(onClick = { confirmClear = true }) { Text("Clear all memory", color = RoseExpense) }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all memory?") },
            text = { Text("The advisor forgets every note, lesson and the conversation summary. Your profile and data are not affected.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearMemory(); confirmClear = false },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseExpense)
                ) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun MemoryRow(note: MemoryItem, onPin: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 2.dp)) {
            Text(note.text, fontSize = 12.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when (note.source) {
                        MemorySource.USER -> "You"
                        MemorySource.AGENT -> "Advisor"
                        MemorySource.SYSTEM -> "From chat summary"
                    } + " · " + SimpleDateFormat("d MMM yyyy", Locale.US).format(Date(note.updatedAt)),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onPin) { Text(if (note.pinned) "📌 Pinned" else "Pin", fontSize = 11.sp) }
                TextButton(onClick = onDelete) { Text("Delete", fontSize = 11.sp, color = RoseExpense) }
            }
        }
    }
}

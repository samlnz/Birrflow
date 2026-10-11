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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.RiskQuiz
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.viewmodel.MainViewModel

/** "About Me": everything the user wants the AI advisor to know, in one place. */
@Composable
fun AboutMeTab(viewModel: MainViewModel) {
    val saved by viewModel.userProfile.collectAsStateWithLifecycle()
    var draft by remember(saved) { mutableStateOf(saved) }
    var showQuiz by remember { mutableStateOf(false) }
    val dirty = draft != saved

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("about_me_tab"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("The more the advisor knows, the better its advice", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "Everything here stays on your phone and is given to the AI advisor as context. The advisor can also fill it in when you tell it things in chat.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LinearProgressIndicator(
                        progress = { draft.completeness / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = EmeraldPrimary
                    )
                    Text("${draft.completeness}% complete", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    val missing = draft.missingImportant()
                    if (missing.isNotEmpty()) Text("Still missing: ${missing.joinToString(", ")}", fontSize = 11.sp, color = AmberAccent)
                }
            }
        }

        item {
            SaveBar(dirty = dirty, onSave = { viewModel.saveUserProfile(draft) }, onReset = { draft = saved })
        }

        item {
            FormSection("About you") {
                TextInput("Name", draft.name) { draft = draft.copy(name = it) }
                Choice("Age", listOf("Under 25", "25–34", "35–44", "45–54", "55+"), draft.ageRange) { draft = draft.copy(ageRange = it) }
                TextInput("City / town", draft.city) { draft = draft.copy(city = it) }
                Choice("Answer me in", listOf("English", "Amharic", "Both"), draft.language) { draft = draft.copy(language = it) }
                Choice("Marital status", listOf("Single", "Married", "Divorced", "Widowed"), draft.maritalStatus) { draft = draft.copy(maritalStatus = it) }
                NumberInput("People in household", draft.householdSize?.toString()) { draft = draft.copy(householdSize = it.toIntOrNull()) }
                NumberInput("People who depend on you financially", draft.dependents?.toString()) { draft = draft.copy(dependents = it.toIntOrNull()) }
            }
        }

        item {
            FormSection("Work & income") {
                TextInput("Occupation", draft.occupation) { draft = draft.copy(occupation = it) }
                Choice("Work type", listOf("Salaried", "Business owner", "Freelancer", "Farmer", "Mixed", "Student", "Retired"), draft.employmentType) {
                    draft = draft.copy(employmentType = it)
                }
                Choice("Income is", listOf("Very stable", "Mostly stable", "Irregular", "Seasonal"), draft.incomeStability) { draft = draft.copy(incomeStability = it) }
                NumberInput("Usual monthly income (ETB)", draft.monthlyIncomeEtb?.let { plainNumber(it) }) { draft = draft.copy(monthlyIncomeEtb = it.toDoubleOrNull()) }
                TextInput("Income notes (e.g. salary on the 25th, harvest in Dec)", draft.incomeNotes, singleLine = false) { draft = draft.copy(incomeNotes = it) }
            }
        }

        item {
            FormSection("Business") {
                SwitchRow("I run a business or trade", draft.hasBusiness) { draft = draft.copy(hasBusiness = it) }
                if (draft.hasBusiness) {
                    TextInput("What business? (e.g. retail shop, coffee trade)", draft.businessType) { draft = draft.copy(businessType = it) }
                    NumberInput("Monthly revenue (ETB)", draft.businessMonthlyRevenueEtb?.let { plainNumber(it) }) { draft = draft.copy(businessMonthlyRevenueEtb = it.toDoubleOrNull()) }
                    NumberInput("Gross margin per sale (%)", draft.businessMarginPct?.let { plainNumber(it) }) { draft = draft.copy(businessMarginPct = it.toDoubleOrNull()) }
                    NumberInput("How many times stock sells out per year", draft.stockTurnsPerYear?.let { plainNumber(it) }) { draft = draft.copy(stockTurnsPerYear = it.toDoubleOrNull()) }
                    TextInput("Business notes (suppliers, seasons, credit customers…)", draft.businessNotes, singleLine = false) { draft = draft.copy(businessNotes = it) }
                }
            }
        }

        item {
            FormSection("Money preferences") {
                SwitchRow("Interest-free only (no interest-based products)", draft.interestFree) { draft = draft.copy(interestFree = it) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Risk level", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            draft.riskLevel?.let { "${it.label} — ${it.description}" } ?: "Not set yet",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = { showQuiz = true }) { Text(if (draft.riskLevel == null) "Take quiz" else "Retake") }
                }
                NumberInput("Investment horizon (months)", draft.horizonMonths?.toString()) { draft = draft.copy(horizonMonths = it.toIntOrNull()) }
                NumberInput("Longest I'm willing to lock money (months)", draft.maxLockMonths?.toString()) { draft = draft.copy(maxLockMonths = it.toIntOrNull()) }
                NumberInput("Emergency reserve to keep (months of spending)", draft.emergencyMonths.toString()) {
                    draft = draft.copy(emergencyMonths = it.toIntOrNull()?.coerceIn(1, 24) ?: 3)
                }
            }
        }

        item {
            FormSection("Goals & obligations") {
                TextInput("My goals (e.g. buy a car in 2 years, grow the shop)", draft.goals, singleLine = false) { draft = draft.copy(goals = it) }
                TextInput("Big expenses coming up (school fees, wedding, rent increase…)", draft.upcomingExpenses, singleLine = false) { draft = draft.copy(upcomingExpenses = it) }
                TextInput("Family I support / regular obligations", draft.familySupport, singleLine = false) { draft = draft.copy(familySupport = it) }
                TextInput("Constraints (e.g. can't travel, need cash every Friday)", draft.constraints, singleLine = false) { draft = draft.copy(constraints = it) }
                TextInput("Anything else the advisor should know about me", draft.aboutMe, singleLine = false) { draft = draft.copy(aboutMe = it) }
            }
        }

        item {
            SaveBar(dirty = dirty, onSave = { viewModel.saveUserProfile(draft) }, onReset = { draft = saved })
        }
    }

    if (showQuiz) {
        RiskQuizDialog(
            onDismiss = { showQuiz = false },
            onDone = { score ->
                draft = draft.copy(riskScore = score, riskLevel = RiskQuiz.levelFor(score))
                showQuiz = false
            }
        )
    }
}

@Composable
private fun SaveBar(dirty: Boolean, onSave: () -> Unit, onReset: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(
            onClick = onSave,
            enabled = dirty,
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
            modifier = Modifier.weight(1f).testTag("about_me_save")
        ) { Text(if (dirty) "Save my profile" else "Saved") }
        if (dirty) TextButton(onClick = onReset) { Text("Undo") }
    }
}

@Composable
private fun FormSection(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun TextInput(label: String, value: String, singleLine: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun NumberInput(label: String, value: String?, onChange: (String) -> Unit) {
    var text by remember { mutableStateOf(value ?: "") }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            text = raw
            onChange(raw.replace(",", "").trim())
        },
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun Choice(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = selected == option,
                    onClick = { onSelect(if (selected == option) "" else option) },
                    label = { Text(option, fontSize = 11.sp) }
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun RiskQuizDialog(onDismiss: () -> Unit, onDone: (Int) -> Unit) {
    val answers = remember { mutableStateListOf<Int?>().apply { repeat(RiskQuiz.QUESTIONS.size) { add(null) } } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Risk quiz") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RiskQuiz.QUESTIONS.forEachIndexed { qi, question ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${qi + 1}. ${question.text}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        question.answers.forEachIndexed { ai, answer ->
                            FilterChip(
                                selected = answers[qi] == ai,
                                onClick = { answers[qi] = ai },
                                label = { Text(answer, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = answers.all { it != null },
                onClick = { onDone(answers.sumOf { it ?: 0 }) }
            ) { Text("See my level") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun plainNumber(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else v.toString()

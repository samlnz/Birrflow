package com.example.ai

import com.example.data.repository.FinancialRepository
import com.example.ui.components.formatCurrency
import org.json.JSONObject

enum class RiskLevel(val label: String, val maxRisk: Int, val description: String) {
    CONSERVATIVE("Conservative", 1, "Protect what I have; only very safe options"),
    BALANCED("Balanced", 2, "Steady growth with limited ups and downs"),
    GROWTH("Growth", 3, "Accept swings for higher long-term growth"),
    AGGRESSIVE("Aggressive", 3, "Maximise growth; comfortable with large swings")
}

/** Everything the user tells BirrFlow about themselves; the advisor reads all of it. */
data class UserProfile(
    // About you
    val name: String = "",
    val ageRange: String = "",
    val city: String = "",
    val language: String = "English",
    val maritalStatus: String = "",
    val householdSize: Int? = null,
    val dependents: Int? = null,
    // Work & income
    val occupation: String = "",
    val employmentType: String = "",
    val incomeStability: String = "",
    val monthlyIncomeEtb: Double? = null,
    val incomeNotes: String = "",
    // Business
    val hasBusiness: Boolean = false,
    val businessType: String = "",
    val businessMonthlyRevenueEtb: Double? = null,
    val businessMarginPct: Double? = null,
    val stockTurnsPerYear: Double? = null,
    val businessNotes: String = "",
    // Money preferences
    val interestFree: Boolean = false,
    val riskLevel: RiskLevel? = null,
    val riskScore: Int? = null,
    val horizonMonths: Int? = null,
    val maxLockMonths: Int? = null,
    val emergencyMonths: Int = 3,
    // Goals, obligations and anything else
    val goals: String = "",
    val upcomingExpenses: String = "",
    val familySupport: String = "",
    val constraints: String = "",
    val aboutMe: String = "",
    val updatedAt: Long = 0L
) {
    /** Fields the advisor would most like to know, still empty. */
    fun missingImportant(): List<String> = buildList {
        if (occupation.isBlank() && employmentType.isBlank()) add("work / occupation")
        if (incomeStability.isBlank()) add("how stable income is")
        if (riskLevel == null) add("risk level (take the quiz)")
        if (horizonMonths == null) add("investment horizon")
        if (goals.isBlank()) add("goals")
        if (dependents == null) add("dependents")
        if (hasBusiness && businessMarginPct == null) add("business margin")
    }

    val completeness: Int
        get() {
            val checks = listOf(
                name.isNotBlank(), ageRange.isNotBlank(), city.isNotBlank(), dependents != null,
                occupation.isNotBlank() || employmentType.isNotBlank(), incomeStability.isNotBlank(), monthlyIncomeEtb != null,
                riskLevel != null, horizonMonths != null, maxLockMonths != null, goals.isNotBlank(),
                upcomingExpenses.isNotBlank() || familySupport.isNotBlank(), aboutMe.isNotBlank() || constraints.isNotBlank(),
                !hasBusiness || businessMarginPct != null
            )
            return checks.count { it } * 100 / checks.size
        }

    fun rankingPreferences(hasDebt: Boolean, debtRatePct: Double?): RankingPreferences = RankingPreferences(
        interestFree = interestFree,
        maxRisk = riskLevel?.maxRisk ?: 3,
        maxLockDays = maxLockMonths?.let { it * 30 } ?: Int.MAX_VALUE,
        hasDebt = hasDebt,
        debtRatePct = debtRatePct,
        businessMarginPct = if (hasBusiness) businessMarginPct else null,
        businessTurnsPerYear = if (hasBusiness) stockTurnsPerYear else null
    )
}

/** Five-question risk quiz; each answer scores 0-3. */
object RiskQuiz {
    data class Question(val text: String, val answers: List<String>)

    val QUESTIONS = listOf(
        Question("If an investment of yours fell 20% in a month, you would…", listOf("Sell everything", "Sell some", "Wait it out", "Buy more")),
        Question("How long could you leave money untouched?", listOf("Under 3 months", "3–12 months", "1–3 years", "More than 3 years")),
        Question("How steady is your income?", listOf("Very irregular", "Somewhat irregular", "Stable", "Stable with savings behind it")),
        Question("Your main money goal is to…", listOf("Protect what I have", "Grow steadily", "Grow faster with some risk", "Grow as fast as possible")),
        Question("Emergency savings you could live on:", listOf("None", "Under 1 month", "1–3 months", "More than 3 months"))
    )

    fun levelFor(score: Int): RiskLevel = when {
        score <= 3 -> RiskLevel.CONSERVATIVE
        score <= 7 -> RiskLevel.BALANCED
        score <= 11 -> RiskLevel.GROWTH
        else -> RiskLevel.AGGRESSIVE
    }
}

object UserProfileService {
    private const val KEY_PROFILE = "USER_PROFILE"

    /** Profile fields the agent may fill in when the user tells it about themselves. */
    val AGENT_EDITABLE_FIELDS = listOf(
        "name", "ageRange", "city", "language", "maritalStatus", "householdSize", "dependents",
        "occupation", "employmentType", "incomeStability", "monthlyIncomeEtb", "incomeNotes",
        "hasBusiness", "businessType", "businessMonthlyRevenueEtb", "businessMarginPct", "stockTurnsPerYear", "businessNotes",
        "interestFree", "horizonMonths", "maxLockMonths", "emergencyMonths",
        "goals", "upcomingExpenses", "familySupport", "constraints", "aboutMe"
    )

    suspend fun load(repository: FinancialRepository): UserProfile {
        val raw = repository.getState(KEY_PROFILE) ?: return UserProfile()
        return try { fromJson(JSONObject(raw)) } catch (_: Exception) { UserProfile() }
    }

    suspend fun save(repository: FinancialRepository, profile: UserProfile) {
        repository.putState(KEY_PROFILE, toJson(profile.copy(updatedAt = System.currentTimeMillis())).toString())
    }

    fun toJson(p: UserProfile): JSONObject = JSONObject().apply {
        put("name", p.name); put("ageRange", p.ageRange); put("city", p.city); put("language", p.language)
        put("maritalStatus", p.maritalStatus)
        p.householdSize?.let { put("householdSize", it) }
        p.dependents?.let { put("dependents", it) }
        put("occupation", p.occupation); put("employmentType", p.employmentType); put("incomeStability", p.incomeStability)
        p.monthlyIncomeEtb?.let { put("monthlyIncomeEtb", it) }
        put("incomeNotes", p.incomeNotes)
        put("hasBusiness", p.hasBusiness); put("businessType", p.businessType)
        p.businessMonthlyRevenueEtb?.let { put("businessMonthlyRevenueEtb", it) }
        p.businessMarginPct?.let { put("businessMarginPct", it) }
        p.stockTurnsPerYear?.let { put("stockTurnsPerYear", it) }
        put("businessNotes", p.businessNotes)
        put("interestFree", p.interestFree)
        p.riskLevel?.let { put("riskLevel", it.name) }
        p.riskScore?.let { put("riskScore", it) }
        p.horizonMonths?.let { put("horizonMonths", it) }
        p.maxLockMonths?.let { put("maxLockMonths", it) }
        put("emergencyMonths", p.emergencyMonths)
        put("goals", p.goals); put("upcomingExpenses", p.upcomingExpenses); put("familySupport", p.familySupport)
        put("constraints", p.constraints); put("aboutMe", p.aboutMe); put("updatedAt", p.updatedAt)
    }

    fun fromJson(o: JSONObject): UserProfile {
        fun optInt(key: String): Int? = if (o.has(key)) o.optInt(key) else null
        fun optDouble(key: String): Double? = if (o.has(key)) o.optDouble(key).takeIf { !it.isNaN() } else null
        return UserProfile(
            name = o.optString("name"), ageRange = o.optString("ageRange"), city = o.optString("city"),
            language = o.optString("language", "English"), maritalStatus = o.optString("maritalStatus"),
            householdSize = optInt("householdSize"), dependents = optInt("dependents"),
            occupation = o.optString("occupation"), employmentType = o.optString("employmentType"),
            incomeStability = o.optString("incomeStability"), monthlyIncomeEtb = optDouble("monthlyIncomeEtb"),
            incomeNotes = o.optString("incomeNotes"),
            hasBusiness = o.optBoolean("hasBusiness", false), businessType = o.optString("businessType"),
            businessMonthlyRevenueEtb = optDouble("businessMonthlyRevenueEtb"), businessMarginPct = optDouble("businessMarginPct"),
            stockTurnsPerYear = optDouble("stockTurnsPerYear"), businessNotes = o.optString("businessNotes"),
            interestFree = o.optBoolean("interestFree", false),
            riskLevel = o.optString("riskLevel").takeIf { it.isNotBlank() }?.let { name -> RiskLevel.values().firstOrNull { it.name == name } },
            riskScore = optInt("riskScore"), horizonMonths = optInt("horizonMonths"), maxLockMonths = optInt("maxLockMonths"),
            emergencyMonths = o.optInt("emergencyMonths", 3),
            goals = o.optString("goals"), upcomingExpenses = o.optString("upcomingExpenses"), familySupport = o.optString("familySupport"),
            constraints = o.optString("constraints"), aboutMe = o.optString("aboutMe"), updatedAt = o.optLong("updatedAt", 0L)
        )
    }

    /** Applies one agent-requested change; returns null when the field or value is not acceptable. */
    fun withField(profile: UserProfile, field: String, value: String): UserProfile? {
        if (field !in AGENT_EDITABLE_FIELDS) return null
        val json = toJson(profile)
        val v = value.trim()
        when (field) {
            "householdSize", "dependents", "horizonMonths", "maxLockMonths", "emergencyMonths" -> json.put(field, v.filter { it.isDigit() }.toIntOrNull() ?: return null)
            "monthlyIncomeEtb", "businessMonthlyRevenueEtb", "businessMarginPct", "stockTurnsPerYear" ->
                json.put(field, v.replace(",", "").replace("%", "").trim().toDoubleOrNull() ?: return null)
            "hasBusiness", "interestFree" -> json.put(field, v.equals("true", true) || v.equals("yes", true))
            else -> json.put(field, v.take(1000))
        }
        return fromJson(json)
    }

    /** Facts block for the advisor's system instruction. */
    fun toPromptSection(p: UserProfile): String = buildString {
        appendLine("ABOUT THE USER (written by the user in the app; respect it):")
        fun line(label: String, value: String?) { if (!value.isNullOrBlank()) appendLine("• $label: $value") }
        line("Name", p.name)
        line("Age", p.ageRange)
        line("City", p.city)
        line("Household", listOfNotNull(p.maritalStatus.takeIf { it.isNotBlank() }, p.householdSize?.let { "$it people" }, p.dependents?.let { "$it dependents" }).joinToString(", "))
        line("Work", listOf(p.occupation, p.employmentType).filter { it.isNotBlank() }.joinToString(", "))
        line("Income stability", p.incomeStability)
        line("Monthly income (user estimate)", p.monthlyIncomeEtb?.let { formatCurrency(it) })
        line("Income notes", p.incomeNotes)
        if (p.hasBusiness) {
            line("Business", listOfNotNull(
                p.businessType.takeIf { it.isNotBlank() },
                p.businessMonthlyRevenueEtb?.let { "revenue ${formatCurrency(it)}/month" },
                p.businessMarginPct?.let { "margin $it%" },
                p.stockTurnsPerYear?.let { "stock turns $it times a year" }
            ).joinToString(", "))
            line("Business notes", p.businessNotes)
        }
        appendLine("• Interest: ${if (p.interestFree) "INTEREST-FREE ONLY — never suggest interest-based products; use interest-free (mudarabah, wadiah) alternatives" else "interest-based products are fine"}")
        appendLine("• Risk level: ${p.riskLevel?.let { "${it.label} (${it.description})" } ?: "not set — be conservative and ask"}")
        line("Investment horizon", p.horizonMonths?.let { "$it months" })
        line("Longest the user will lock money", p.maxLockMonths?.let { "$it months" })
        appendLine("• Emergency reserve the user wants: ${p.emergencyMonths} months of spending")
        line("Goals", p.goals)
        line("Upcoming big expenses", p.upcomingExpenses)
        line("Family support / obligations", p.familySupport)
        line("Constraints", p.constraints)
        line("Anything else", p.aboutMe)
        if (p.language.equals("Amharic", true)) appendLine("• Answer in Amharic (keep numbers in ETB).")
        if (p.language.equals("Both", true)) appendLine("• Answer in English with a short Amharic summary.")
        val missing = p.missingImportant()
        if (missing.isNotEmpty()) appendLine("• Not known yet: ${missing.joinToString(", ")} — ask about one of these when it matters, and save the answer with update_user_profile.")
    }.trimEnd()
}

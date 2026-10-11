package com.example.ai

import com.example.data.entity.CategoryEntity
import com.example.data.entity.FlowDirection
import com.example.data.entity.TransactionEntity
import com.example.data.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryLearnerTest {

    private val food = CategoryEntity("cat_food", "Food", "restaurant", "#F59E0B", description = "Groceries, supermarket and restaurants")
    private val transport = CategoryEntity("cat_transport", "Transport", "directions_car", "#3B82F6", description = "Ride taxi fuel and bus fares")
    private val categories = listOf(food, transport)

    private fun debit(id: String, amount: Double, merchant: String?, categoryId: String?, description: String = "") = TransactionEntity(
        transactionId = id,
        phoneId = "P1",
        financialAccountId = "telebirr",
        sourceId = "telebirr",
        rawSmsId = id,
        transactionType = TransactionType.EXPENSE,
        amount = amount,
        transactionDate = "",
        transactionTime = "",
        merchant = merchant,
        description = description,
        categoryId = categoryId,
        direction = FlowDirection.DEBIT
    )

    @Test
    fun learnsPayeeFromPastChoices() {
        val history = listOf(
            debit("1", 450.0, "Shoa Supermarket", "cat_food"),
            debit("2", 520.0, "Shoa Supermarket", "cat_food"),
            debit("3", 480.0, "Shoa Supermarket", "cat_food"),
            debit("4", 150.0, "Feres Ride", "cat_transport")
        )
        val learner = CategoryLearner.train(history, categories)
        val top = learner.suggest(debit("n", 500.0, "SHOA supermarket!", null)).first()
        assertEquals("cat_food", top.categoryId)
        assertTrue(top.confidence > 0.5)
        assertEquals("cat_food", learner.certainCategory(debit("n2", 300.0, "Shoa Supermarket", null)))
        // Only one earlier Feres debit: not certain enough to auto-file
        assertNull(learner.certainCategory(debit("n3", 120.0, "Feres Ride", null)))
    }

    @Test
    fun usesCategoryDescriptionsWithoutHistory() {
        val learner = CategoryLearner.train(emptyList(), categories)
        val top = learner.suggest(debit("n", 200.0, "City Taxi", null, "Paid for taxi ride")).first()
        assertEquals("cat_transport", top.categoryId)
    }
}

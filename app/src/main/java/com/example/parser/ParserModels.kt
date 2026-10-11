package com.example.parser

import com.example.data.entity.FlowDirection
import com.example.data.entity.SmsSenderIdentifierEntity
import com.example.data.entity.TransactionType
import com.example.data.entity.VerificationStatus

data class ParsedSmsResult(
    val detectedSourceId: String?,
    val detectedSourceName: String?,
    val detectedFormatId: String?,
    val formatName: String?,
    val transactionType: TransactionType,
    val amount: Double?,
    val currency: String = "ETB",
    val transactionDate: String?,
    val transactionTime: String?,
    val sender: String?,
    val recipient: String?,
    val merchant: String?,
    val accountIdentifier: String?,
    val referenceNumber: String?,
    val balanceAfter: Double?,
    val fee: Double = 0.0,
    val description: String,
    val confidence: Float, // 0.0 to 1.0
    val verificationStatus: VerificationStatus,
    val validationErrors: List<String> = emptyList(),
    val parserVersion: Int = 1,
    val isUnknownSender: Boolean = false,
    val matchedSenderIdentifierId: String? = null,
    val direction: FlowDirection = FlowDirection.UNKNOWN,
    // Stable key of the individual account/wallet this SMS belongs to (e.g. "cbe:0032"), used for balance chains
    val accountKey: String? = null
)

data class SourceDetectionResult(
    val sourceId: String,
    val sourceName: String,
    val confidence: Float,
    val matchedIdentifierId: String? = null
)

data class SenderTestResult(
    val matchedInstitutionId: String?,
    val matchedInstitutionName: String?,
    val matchedIdentifier: SmsSenderIdentifierEntity?,
    val isApplicable: Boolean,
    val reason: String
)

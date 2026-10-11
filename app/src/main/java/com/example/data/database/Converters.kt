package com.example.data.database

import androidx.room.TypeConverter
import com.example.data.entity.FlowDirection
import com.example.data.entity.ManualSourceKind
import com.example.data.entity.MatchMethod
import com.example.data.entity.SenderIdentifierType
import com.example.data.entity.SmsProcessingStatus
import com.example.data.entity.SourceType
import com.example.data.entity.TransactionType
import com.example.data.entity.VerificationStatus
import com.example.data.entity.WealthKind

class Converters {
    @TypeConverter
    fun fromTransactionType(value: TransactionType): String = value.name

    @TypeConverter
    fun toTransactionType(value: String): TransactionType = try {
        TransactionType.valueOf(value)
    } catch (_: Exception) {
        TransactionType.UNKNOWN
    }

    @TypeConverter
    fun fromSmsProcessingStatus(value: SmsProcessingStatus): String = value.name

    @TypeConverter
    fun toSmsProcessingStatus(value: String): SmsProcessingStatus = try {
        SmsProcessingStatus.valueOf(value)
    } catch (_: Exception) {
        SmsProcessingStatus.UNKNOWN_FORMAT
    }

    @TypeConverter
    fun fromVerificationStatus(value: VerificationStatus): String = value.name

    @TypeConverter
    fun toVerificationStatus(value: String): VerificationStatus = try {
        VerificationStatus.valueOf(value)
    } catch (_: Exception) {
        VerificationStatus.NEEDS_REVIEW
    }

    @TypeConverter
    fun fromSourceType(value: SourceType): String = value.name

    @TypeConverter
    fun toSourceType(value: String): SourceType = try {
        SourceType.valueOf(value)
    } catch (_: Exception) {
        SourceType.OTHER
    }

    @TypeConverter
    fun fromSenderIdentifierType(value: SenderIdentifierType): String = value.name

    @TypeConverter
    fun toSenderIdentifierType(value: String): SenderIdentifierType = try {
        SenderIdentifierType.valueOf(value)
    } catch (_: Exception) {
        SenderIdentifierType.NAME
    }

    @TypeConverter
    fun fromMatchMethod(value: MatchMethod): String = value.name

    @TypeConverter
    fun toMatchMethod(value: String): MatchMethod = try {
        MatchMethod.valueOf(value)
    } catch (_: Exception) {
        MatchMethod.CASE_INSENSITIVE
    }

    @TypeConverter
    fun fromFlowDirection(value: FlowDirection): String = value.name

    @TypeConverter
    fun toFlowDirection(value: String): FlowDirection = try {
        FlowDirection.valueOf(value)
    } catch (_: Exception) {
        FlowDirection.UNKNOWN
    }

    @TypeConverter
    fun fromManualSourceKind(value: ManualSourceKind): String = value.name

    @TypeConverter
    fun toManualSourceKind(value: String): ManualSourceKind = try {
        ManualSourceKind.valueOf(value)
    } catch (_: Exception) {
        ManualSourceKind.OTHER
    }

    @TypeConverter
    fun fromWealthKind(value: WealthKind): String = value.name

    @TypeConverter
    fun toWealthKind(value: String): WealthKind = try {
        WealthKind.valueOf(value)
    } catch (_: Exception) {
        WealthKind.OTHER_ASSET
    }
}

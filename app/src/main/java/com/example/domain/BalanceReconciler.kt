package com.example.domain

import com.example.data.entity.FlowDirection
import kotlin.math.abs
import kotlin.math.round

/**
 * Rebuilds the balance chain of one account (one phone + one wallet/bank account) and finds money
 * movements that no parsed SMS explains.
 *
 * Every Telebirr / CBE / BOA alert carries the balance after the transaction. For two consecutive
 * alerts A and B:
 *
 *     expected balance before B = B.balanceAfter - (B credit) or + (B debit + fee)
 *
 * If that differs from A.balanceAfter, a credit or debit happened in between that we never saw
 * (SMS never delivered, unknown wording, sent to another SIM...). The difference is exactly its value.
 */
object BalanceReconciler {

    const val TOLERANCE = 0.01
    private const val REORDER_WINDOW_MS = 10 * 60 * 1000L

    data class Entry(
        val id: String,
        val timestamp: Long,
        val direction: FlowDirection,
        val amount: Double,
        val fee: Double,
        val balanceAfter: Double?
    ) {
        /** Signed effect on the account balance; null when the direction is unknown. */
        val delta: Double?
            get() = when (direction) {
                FlowDirection.CREDIT -> amount
                FlowDirection.DEBIT -> -(amount + fee)
                FlowDirection.UNKNOWN -> null
            }
    }

    /** A balance seen in an SMS we could not turn into a transaction (unknown format). */
    data class Anchor(
        val rawSmsId: String,
        val timestamp: Long,
        val balance: Double
    )

    data class Gap(
        /** Id of the entry/anchor right after the gap; stable across re-runs. */
        val beforeEventId: String,
        val amount: Double,
        val direction: FlowDirection,
        val fromTimestamp: Long,
        val toTimestamp: Long,
        val balanceBefore: Double,
        val balanceAfter: Double,
        /** Set when the gap is fully explained by one unrecognized SMS. */
        val unparsedRawSmsId: String? = null
    )

    private sealed class Event(val id: String, val timestamp: Long) {
        class Tx(val entry: Entry) : Event(entry.id, entry.timestamp)
        class Bal(val anchor: Anchor) : Event(anchor.rawSmsId, anchor.timestamp)
    }

    fun findGaps(entries: List<Entry>, anchors: List<Anchor> = emptyList()): List<Gap> {
        val events: MutableList<Event> = (entries.map { Event.Tx(it) } + anchors.map { Event.Bal(it) })
            .sortedBy { it.timestamp }
            .toMutableList()
        fixOutOfOrderDelivery(events)

        val gaps = mutableListOf<Gap>()
        var lastBalance: Double? = null
        var lastTimestamp = 0L

        for (event in events) {
            when (event) {
                is Event.Tx -> {
                    val e = event.entry
                    val delta = e.delta
                    val after = e.balanceAfter
                    if (after != null && delta != null) {
                        val expectedBefore = round2(after - delta)
                        lastBalance?.let { prev ->
                            val diff = round2(expectedBefore - prev)
                            if (abs(diff) >= TOLERANCE) {
                                gaps += Gap(
                                    beforeEventId = e.id,
                                    amount = abs(diff),
                                    direction = if (diff > 0) FlowDirection.CREDIT else FlowDirection.DEBIT,
                                    fromTimestamp = lastTimestamp,
                                    toTimestamp = e.timestamp,
                                    balanceBefore = prev,
                                    balanceAfter = expectedBefore
                                )
                            }
                        }
                        lastBalance = after
                    } else if (after != null) {
                        // Direction unknown but the new balance is known: treat as an anchor
                        lastBalance?.let { prev ->
                            val diff = round2(after - prev)
                            if (abs(diff) >= TOLERANCE) {
                                gaps += Gap(e.id, abs(diff), if (diff > 0) FlowDirection.CREDIT else FlowDirection.DEBIT,
                                    lastTimestamp, e.timestamp, prev, after)
                            }
                        }
                        lastBalance = after
                    } else if (delta != null && lastBalance != null) {
                        lastBalance = round2(lastBalance + delta)
                    }
                    lastTimestamp = e.timestamp
                }
                is Event.Bal -> {
                    val a = event.anchor
                    lastBalance?.let { prev ->
                        val diff = round2(a.balance - prev)
                        if (abs(diff) >= TOLERANCE) {
                            gaps += Gap(
                                beforeEventId = a.rawSmsId,
                                amount = abs(diff),
                                direction = if (diff > 0) FlowDirection.CREDIT else FlowDirection.DEBIT,
                                fromTimestamp = lastTimestamp,
                                toTimestamp = a.timestamp,
                                balanceBefore = prev,
                                balanceAfter = a.balance,
                                unparsedRawSmsId = a.rawSmsId
                            )
                        }
                    }
                    lastBalance = a.balance
                    lastTimestamp = a.timestamp
                }
            }
        }
        return gaps
    }

    /**
     * Operators sometimes deliver two alerts sent seconds apart in the wrong order. When swapping two
     * neighbours that arrived within [REORDER_WINDOW_MS] makes the chain consistent, use that order
     * instead of reporting two fake gaps that cancel each other out.
     */
    private fun fixOutOfOrderDelivery(events: MutableList<Event>) {
        var i = 1
        while (i + 1 < events.size) {
            val prev = balanceOf(events[i - 1])
            val a = events[i]
            val b = events[i + 1]
            if (prev != null && b.timestamp - a.timestamp <= REORDER_WINDOW_MS &&
                !consistent(prev, a) && consistent(prev, b) && balanceOf(b)?.let { consistent(it, a) } == true
            ) {
                events[i] = b
                events[i + 1] = a
                i += 2
            } else {
                i++
            }
        }
    }

    private fun balanceOf(event: Event): Double? = when (event) {
        is Event.Tx -> event.entry.balanceAfter
        is Event.Bal -> event.anchor.balance
    }

    private fun consistent(previousBalance: Double, event: Event): Boolean {
        if (event !is Event.Tx) return false
        val after = event.entry.balanceAfter ?: return false
        val delta = event.entry.delta ?: return false
        return abs(round2(after - delta) - previousBalance) < TOLERANCE
    }

    private fun round2(v: Double): Double = round(v * 100.0) / 100.0
}

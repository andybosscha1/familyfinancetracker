package com.timmat.financetracker.common

import java.util.Calendar
import java.util.Date

/**
 * A “month” can start on any day 1–28 as configured per family. Everything
 * that used to call [java.util.Calendar] for current-month ranges should flow
 * through [currentBillingCycle] so the same rule applies everywhere.
 */
data class BillingCycle(
    val start: Date,
    val end: Date,
    /** Stable key for “which cycle is this” — safe to compare across launches. */
    val key: String,
) {
    val endExclusive: Date get() = end
}

fun currentBillingCycle(monthStartDay: Int = 1, now: Date = Date()): BillingCycle {
    val day = monthStartDay.coerceIn(1, 28)
    val cal = Calendar.getInstance().apply {
        time = now
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    if (cal.get(Calendar.DAY_OF_MONTH) < day) {
        cal.add(Calendar.MONTH, -1)
    }
    cal.set(Calendar.DAY_OF_MONTH, day)
    val start = cal.time
    val y = cal.get(Calendar.YEAR); val m = cal.get(Calendar.MONTH)
    cal.add(Calendar.MONTH, 1)
    val end = cal.time
    return BillingCycle(start, end, "$y-$m-$day")
}

/** Returns the start date of the cycle that is [months] cycles before the current one. */
fun cycleStartMonthsAgo(monthStartDay: Int, months: Int, now: Date = Date()): Date {
    val current = currentBillingCycle(monthStartDay, now)
    val cal = Calendar.getInstance().apply {
        time = current.start
        add(Calendar.MONTH, -(months - 1))
    }
    return cal.time
}

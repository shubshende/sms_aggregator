package com.example.smsaggregator.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Date helpers that avoid per-item allocations in hot paths (list rows, filters).
 *
 * - monthRange returns epoch bounds so filtering becomes a cheap `date in start until end`
 *   comparison instead of constructing a Calendar for every transaction.
 * - The formatters are reused. SimpleDateFormat isn't thread-safe, so only call format
 *   helpers from the UI thread (which is where list rows render).
 */
object DateUtils {

    fun monthRange(year: Int, month: Int): Pair<Long, Long> {
        val c = Calendar.getInstance()
        c.set(year, month, 1, 0, 0, 0)
        c.set(Calendar.MILLISECOND, 0)
        val start = c.timeInMillis
        c.add(Calendar.MONTH, 1)
        return start to c.timeInMillis
    }

    private val dayMonthFmt = SimpleDateFormat("dd MMM", Locale.getDefault())

    fun dayMonth(timestamp: Long): String = dayMonthFmt.format(Date(timestamp))
}

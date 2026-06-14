package com.example.smsaggregator.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.smsaggregator.R
import com.example.smsaggregator.ui.MainActivity
import java.text.NumberFormat
import java.util.Locale

/**
 * Home-screen widget showing this month's spend, remaining budget, and income.
 *
 * To stay fast and off the main thread, the widget never touches the database directly.
 * The app writes a small "snapshot" into SharedPreferences whenever data changes
 * (see TransactionViewModel.updateWidgetSnapshot) and the widget just renders that.
 */
class ExpenseWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> renderWidget(context, appWidgetManager, id) }
    }

    companion object {
        private fun fmt(v: Double): String =
            NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0 }.format(v)

        fun renderWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val prefs = context.getSharedPreferences("sms_agg_prefs", Context.MODE_PRIVATE)
            val spent = prefs.getFloat("widget_spent", 0f).toDouble()
            val income = prefs.getFloat("widget_income", 0f).toDouble()
            val budget = prefs.getFloat("widget_budget", 0f).toDouble()

            val views = RemoteViews(context.packageName, R.layout.widget_expense)
            views.setTextViewText(R.id.widget_spent, fmt(spent))
            views.setTextViewText(
                R.id.widget_budget,
                if (budget > 0) "of ${fmt(budget)} budget" else "tap to open"
            )
            views.setTextViewText(
                R.id.widget_income,
                if (income > 0) "+ ${fmt(income)} received" else ""
            )

            val intent = Intent(context, MainActivity::class.java)
            val pi = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_root, pi)

            manager.updateAppWidget(widgetId, views)
        }

        /** Called by the app when the snapshot changes so the widget refreshes immediately. */
        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ExpenseWidgetProvider::class.java))
            ids.forEach { id -> renderWidget(context, manager, id) }
        }
    }
}

package com.example.smsaggregator.ui

fun getCategoryEmoji(category: String): String {
    return when (category) {
        "Food & Dining" -> "🍔"
        "Groceries" -> "🛒"
        "Gold & Jewellery" -> "💍"
        "Fuel" -> "⛽"
        "Shopping" -> "🛍️"
        "Travel" -> "✈️"
        "Utilities" -> "💡"
        "Telecom" -> "📱"
        "Entertainment" -> "🎬"
        "Health" -> "🏥"
        "Fitness" -> "💪"
        "Education" -> "📚"
        "Insurance" -> "🛡️"
        "Investments", "Investment" -> "📈"
        "EMI / Loan" -> "🏦"
        "Rent" -> "🏠"
        "Auto Debit" -> "🔄"
        "UPI Transfer" -> "💸"
        "Wallet Topup" -> "👛"
        "ATM" -> "🏧"
        "Government" -> "🏛️"
        "Home & Lifestyle" -> "🏡"
        else -> "📋"
    }
}

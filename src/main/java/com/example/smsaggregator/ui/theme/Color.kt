package com.example.smsaggregator.ui.theme

import androidx.compose.ui.graphics.Color

// ═══════ LIGHT PALETTE ═══════
val Light_Surface        = Color(0xFFFBF8F1)
val Light_SurfCont       = Color(0xFFF3EEE3)
val Light_SurfVar        = Color(0xFFE6DFCD)
val Light_OnSurface      = Color(0xFF1D1A14)
val Light_OnSurfVar      = Color(0xFF665F52)
val Light_Outline        = Color(0xFF948C7C)
val Light_OutlineVar     = Color(0xFFDCD5C3)
val Light_Primary        = Color(0xFF8A5A1C)
val Light_OnPrimary      = Color(0xFFFFFFFF)
val Light_PrimContainer  = Color(0xFFFFDEA8)
val Light_OnPrimCont     = Color(0xFF2E1C00)
val Light_Error          = Color(0xFFA63D30)

// ═══════ DARK PALETTE ═══════
val Dark_Surface         = Color(0xFF141210)
val Dark_SurfCont        = Color(0xFF1F1C18)
val Dark_SurfContHigh    = Color(0xFF2A2620)
val Dark_SurfVar         = Color(0xFF322D27)
val Dark_OnSurface       = Color(0xFFF1ECE3)
val Dark_OnSurfVar       = Color(0xFFB6ADA0)
val Dark_Outline         = Color(0xFF5C564D)
val Dark_OutlineVar      = Color(0xFF332E28)
val Dark_Primary         = Color(0xFFE8C37A)
val Dark_OnPrimary       = Color(0xFF2A1E08)
val Dark_PrimContainer   = Color(0xFF5C4418)
val Dark_OnPrimCont      = Color(0xFFFFE1A8)
val Dark_Error           = Color(0xFFF0A098)

// ═══════ CATEGORY TONES ═══════
// Each category has: tone (text/icon/arc), bg (chip background)
object CatColor {
    // Dark mode
    val food     = Color(0xFFC97A2A)
    val foodBg   = Color(0xFF3A2818)
    val trans    = Color(0xFF2E6BC4)
    val transBg  = Color(0xFF1C2A3E)
    val groc     = Color(0xFF2D8A5A)
    val grocBg   = Color(0xFF1A3228)
    val bills    = Color(0xFF7A4FC4)
    val billsBg  = Color(0xFF2E2040)
    val shop     = Color(0xFFC44872)
    val shopBg   = Color(0xFF3A1F2A)
    val subs     = Color(0xFF2D8A5A)
    val subsBg   = Color(0xFF1A3228)
    val health   = Color(0xFF2E6BC4)
    val healthBg = Color(0xFF1C2A3E)
    val other    = Color(0xFF8A7A30)
    val otherBg  = Color(0xFF2E2A18)

    fun tone(cat: String): Color = when {
        cat.contains("Income", true) -> Color(0xFF2E7D32)
        cat.contains("Transfer", true) -> Color(0xFF607D8B)
        cat.contains("Food", true) || cat.contains("Dining", true) -> food
        cat.contains("Transport", true) || cat.contains("Travel", true) || cat.contains("Fuel", true) -> trans
        cat.contains("Grocer", true) -> groc
        cat.contains("Bill", true) || cat.contains("Utilit", true) || cat.contains("Telecom", true) || cat.contains("Rent", true) -> bills
        cat.contains("Shop", true) || cat.contains("Gold", true) -> shop
        cat.contains("Entertain", true) || cat.contains("Subscript", true) -> subs
        cat.contains("Health", true) || cat.contains("Fitness", true) || cat.contains("Insurance", true) -> health
        cat.contains("Invest", true) || cat.contains("EMI", true) -> Color(0xFF4A90D9)
        else -> other
    }

    fun bg(cat: String): Color = when {
        cat.contains("Food", true) || cat.contains("Dining", true) -> foodBg
        cat.contains("Transport", true) || cat.contains("Travel", true) || cat.contains("Fuel", true) -> transBg
        cat.contains("Grocer", true) -> grocBg
        cat.contains("Bill", true) || cat.contains("Utilit", true) || cat.contains("Telecom", true) || cat.contains("Rent", true) -> billsBg
        cat.contains("Shop", true) || cat.contains("Gold", true) -> shopBg
        cat.contains("Entertain", true) || cat.contains("Subscript", true) -> subsBg
        cat.contains("Health", true) || cat.contains("Fitness", true) || cat.contains("Insurance", true) -> healthBg
        else -> otherBg
    }

    fun icon(cat: String): String = when {
        cat.contains("Income", true) -> "💰"
        cat.contains("Transfer", true) -> "🔄"
        cat.contains("Food", true) || cat.contains("Dining", true) -> "☕"
        cat.contains("Transport", true) || cat.contains("Travel", true) || cat.contains("Fuel", true) -> "🚌"
        cat.contains("Grocer", true) -> "🛒"
        cat.contains("Bill", true) || cat.contains("Utilit", true) || cat.contains("Telecom", true) || cat.contains("Rent", true) -> "🧾"
        cat.contains("Shop", true) || cat.contains("Gold", true) -> "🛍️"
        cat.contains("Entertain", true) || cat.contains("Subscript", true) -> "▶"
        cat.contains("Health", true) || cat.contains("Fitness", true) || cat.contains("Insurance", true) -> "❤️"
        cat.contains("Invest", true) || cat.contains("EMI", true) -> "📊"
        cat.contains("ATM", true) -> "🏧"
        cat.contains("UPI", true) || cat.contains("Wallet", true) -> "📱"
        cat.contains("Govern", true) -> "🏛️"
        cat.contains("Educat", true) -> "📚"
        else -> "⚫"
    }
}

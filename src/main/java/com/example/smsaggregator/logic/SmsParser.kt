package com.example.smsaggregator.logic

import android.util.Log
import com.example.smsaggregator.data.Transaction
import com.example.smsaggregator.data.TransactionType
import java.util.regex.Pattern

/**
 * Robust Indian-bank/credit-card SMS parser.
 *
 * Design goals
 * ------------
 * 1. NEVER parse personal-phone-number senders as bank SMS (key cause of false positives).
 * 2. Recognise India's DLT sender headers (e.g. "VK-HDFCBK-S", "JD-SBIINB-T", "AD-AXISBK-P")
 *    where the first 2 chars are operator code, middle 4-6 chars are the entity short code,
 *    and the optional last letter is the consent category (S=Service, T=Transactional, P=Promo,
 *    G=Govt). Reject promotional category outright.
 * 3. Use a multi-stage rejection pipeline (OTP / promo / balance / future / failed / refund)
 *    BEFORE attempting amount extraction. This is what kills false positives.
 * 4. Extract amount AFTER stripping non-transaction monetary clauses ("Avl Bal", "Avl Limit",
 *    "cashback of Rs.X earned", "if not you call Rs..."). Indian lakh-notation supported.
 * 5. Capture BOTH debit and credit transactions (refunds, salary, cashback) — earlier versions
 *    silently dropped credits, which lost useful data.
 * 6. Tighter merchant cleanup: trim ref numbers, UPI handles tails, dates, "Avl bal" tails,
 *    "Not you?" tails, "Call 1800..." tails.
 * 7. Last-4 digit extraction anchored to "card"/"a/c"/"acct"/"ending in" only — never grabs
 *    arbitrary 4-digit ref numbers.
 * 8. Credit-card bill SMS parsing uses dedicated "Total Amount Due" patterns instead of
 *    grabbing the first amount in the body.
 */
object SmsParser {

    // ---------------------------------------------------------------------------------------
    // 1. Trusted senders & sender-ID parsing
    // ---------------------------------------------------------------------------------------

    /**
     * Core entity short codes seen in DLT headers for banks, card issuers, NBFCs, wallets,
     * BNPL apps, and meal-card programs. Match is substring-on-uppercase.
     */
    private val TRUSTED_ENTITY_CODES = listOf(
        // Public sector banks
        "SBIINB", "SBIBNK", "SBIPSG", "SBICRD", "SBICARD",
        "PNBSMS", "PNBBNK", "BOIIND", "BOIIBN", "CANBNK", "CNRBNK", "UNIONB", "UBIBNK",
        "IOBCHN", "IOBBNK", "CBSSBI", "BOBSMS", "BOBTXN", "UCOBNK",
        // Private banks
        "HDFCBK", "HDFCBN", "HDFCBANK",
        "ICICIB", "ICICIBK", "ICICI",
        "AXISBK", "AXISB", "AXISBANK",
        "KOTAKB", "KOTAK",
        "YESBNK", "YESBK",
        "INDUSB", "INDUSIND",
        "IDFCBK", "IDFCFB", "IDFC",
        "RBLBNK", "RBL",
        "FEDBNK", "FEDERALBANK", "FEDBANK",
        "BANDHAN", "DCBBANK", "CSBBANK", "SOUTHIB", "SIBINB",
        "IDBIBANK", "IDBIBK",
        "DBSBNK", "DBSBANK",
        "AUBANK", "AUSFB",
        "EQUITAS", "ESAFBK",
        // Foreign banks
        "CITIBK", "CITIBANK", "CITIBNK",
        "SCBANK", "SCBINB", "SCBIND",
        "HSBCIN", "HSBC",
        "AMEXIN", "AMEX",
        // Card issuers (often separate DLT entities)
        "HDFCCC", "ICICICC", "AXISCC", "INDUSCCC", "RBLCC",
        // Payments / UPI / wallets
        "PHONEPE", "PAYTM", "PAYTMB", "PAYTMSB", "GPAYBN", "GPAY", "BHIMUPI", "BHIM",
        "MOBIKWK", "MOBIKWIK", "FREECHARGE", "JIOMNY", "AIRTELM", "AIRTELP",
        "WHATSPAY", "WHATSAPP",
        // BNPL & credit lines
        "LAZYPAY", "SIMPL", "ZESTMNY", "ZESTMONEY", "SLICEIT", "SLICE", "UNICRDS",
        "KREDITB", "OLAMONT", "FLEXI", "CREDITRD",
        // Meal cards
        "PLUXEE", "SODEXO", "ZETA", "EDENRED", "ZAGGLE",
        // Loan/EMI providers occasionally seen
        "BAJAJF", "BAJAJFIN", "TATACAP", "MMFSL"
    )

    /**
     * Heuristic: senderId looks like a phone number?
     * Catches +91xxxxxxxxxx, 91xxxxxxxxxx, 10-digit Indian mobile, longline numbers like
     * 08001234567. We treat anything with >= 6 digits as phone-like.
     */
    private val PHONE_LIKE_SENDER = Regex("^\\+?\\d[\\d\\s-]{5,}$")

    /**
     * DLT sender pattern: 2-char operator code, dash, 4-8 char entity code, optional dash + 1
     * letter consent category. Examples: VK-HDFCBK-S, JD-SBIINB-T, AD-AXISBK-P.
     * Also allows the older 6-char-only format used pre-DLT and during transitions.
     */
    private val DLT_SENDER = Regex("^([A-Z]{2}-)?[A-Z]{2,8}(-[STPG])?$", RegexOption.IGNORE_CASE)

    private fun normalizeSender(raw: String): String = raw.trim().uppercase()

    private fun isPhoneNumberSender(senderId: String): Boolean {
        return PHONE_LIKE_SENDER.matches(senderId.trim()) && senderId.count { it.isDigit() } >= 6
    }

    /** True if sender's DLT consent category is "P" (promo). Those never carry real txns. */
    private fun isPromoCategorySender(senderId: String): Boolean {
        val s = normalizeSender(senderId)
        return s.endsWith("-P")
    }

    private fun isTrustedSender(senderId: String): Boolean {
        val s = normalizeSender(senderId)
        return TRUSTED_ENTITY_CODES.any { s.contains(it) }
    }

    // ---------------------------------------------------------------------------------------
    // 2. Payment-type & bank-name detection
    // ---------------------------------------------------------------------------------------

    private val UPI_PATTERN = Regex("\\bupi\\b|gpay|phonepe|bhim|paytm upi|@ok[a-z]+|@ybl|@axl|@ibl|@upi", RegexOption.IGNORE_CASE)
    private val CREDIT_CARD_PATTERN = Regex("credit\\s*card|\\bcc\\b|c/card|cr\\.?\\s*card", RegexOption.IGNORE_CASE)
    private val DEBIT_CARD_PATTERN = Regex("debit\\s*card|\\bdc\\b|d/card|\\bpos\\b|swipe", RegexOption.IGNORE_CASE)
    private val WALLET_PATTERN = Regex("paytm wallet|paytm bal|mobikwik|freecharge|airtel money|jio money|pluxee|sodexo|meal card|zaggle|ticket restaurant", RegexOption.IGNORE_CASE)
    private val EMI_PATTERN = Regex("\\bemi\\b|equated monthly", RegexOption.IGNORE_CASE)
    private val BNPL_PATTERN = Regex("\\bbnpl\\b|buy now pay later|lazypay|simpl|zestmoney|\\bslice\\b", RegexOption.IGNORE_CASE)
    private val NET_BANKING_PATTERN = Regex("\\bneft\\b|\\brtgs\\b|\\bimps\\b|netbanking|net banking|internet banking", RegexOption.IGNORE_CASE)
    private val AUTO_DEBIT_PATTERN = Regex("\\bnach\\b|auto.?debit|standing instruction|\\bmandate\\b|\\bsi\\b\\s+collection", RegexOption.IGNORE_CASE)
    private val ATM_PATTERN = Regex("\\batm\\b|cash withdrawal|cash wtd|cash w/d", RegexOption.IGNORE_CASE)

    private fun detectPaymentType(sms: String): String = when {
        UPI_PATTERN.containsMatchIn(sms) -> "UPI"
        CREDIT_CARD_PATTERN.containsMatchIn(sms) -> "Credit Card"
        DEBIT_CARD_PATTERN.containsMatchIn(sms) -> "Debit Card"
        WALLET_PATTERN.containsMatchIn(sms) -> "Wallet"
        EMI_PATTERN.containsMatchIn(sms) -> "EMI"
        BNPL_PATTERN.containsMatchIn(sms) -> "BNPL"
        NET_BANKING_PATTERN.containsMatchIn(sms) -> "Net Banking"
        AUTO_DEBIT_PATTERN.containsMatchIn(sms) -> "Auto Debit"
        ATM_PATTERN.containsMatchIn(sms) -> "ATM Withdrawal"
        else -> "Bank Transfer"
    }

    private fun bankNameFromSender(senderId: String): String? {
        val s = normalizeSender(senderId)
        return when {
            s.contains("HDFC") -> "HDFC Bank"
            // SBI Card is a separate entity from SBI bank
            s.contains("SBICRD") || s.contains("SBICARD") -> "SBI Card"
            s.contains("SBI") -> "SBI"
            s.contains("ICICI") -> "ICICI Bank"
            s.contains("AXIS") -> "Axis Bank"
            s.contains("KOTAK") -> "Kotak Bank"
            s.contains("PNB") -> "PNB"
            s.contains("CITI") -> "Citi Bank"
            s.contains("AMEX") -> "Amex"
            s.contains("IDFC") -> "IDFC FIRST"
            s.contains("RBL") -> "RBL Bank"
            s.contains("FED") -> "Federal Bank"
            s.contains("INDUS") -> "IndusInd"
            s.contains("BOI") -> "Bank of India"
            s.contains("BOB") -> "Bank of Baroda"
            s.contains("CAN") -> "Canara Bank"
            s.contains("UNION") || s.contains("UBI") -> "Union Bank"
            s.contains("YES") -> "Yes Bank"
            s.contains("IDBI") -> "IDBI Bank"
            s.contains("DBS") -> "DBS Bank"
            s.contains("HSBC") -> "HSBC"
            s.contains("SCB") -> "Standard Chartered"
            s.contains("AUBANK") || s.contains("AUSFB") -> "AU Small Finance Bank"
            s.contains("BANDHAN") -> "Bandhan Bank"
            s.contains("DCB") -> "DCB Bank"
            s.contains("CSB") -> "CSB Bank"
            s.contains("SOUTHIB") || s.contains("SIBINB") -> "South Indian Bank"
            s.contains("PHONEPE") -> "PhonePe"
            s.contains("PAYTM") -> "Paytm"
            s.contains("GPAY") -> "Google Pay"
            s.contains("MOBIKW") || s.contains("MOBIKWIK") -> "MobiKwik"
            s.contains("FREECHARGE") -> "FreeCharge"
            s.contains("AMAZON") -> "Amazon Pay"
            s.contains("PLUXEE") || s.contains("SODEXO") -> "Pluxee Meal Card"
            s.contains("ZETA") -> "Zeta"
            s.contains("EDENRED") -> "Edenred"
            s.contains("ZAGGLE") -> "Zaggle"
            s.contains("LAZYPAY") -> "LazyPay"
            s.contains("SIMPL") -> "Simpl"
            s.contains("SLICE") -> "Slice"
            s.contains("ZEST") -> "ZestMoney"
            s.contains("BAJAJ") -> "Bajaj Finserv"
            s.contains("TATACAP") -> "Tata Capital"
            else -> null
        }
    }

    // ---------------------------------------------------------------------------------------
    // 3. Rejection-stage patterns (OTP / promo / balance-only / future / failed)
    // ---------------------------------------------------------------------------------------

    /** OTP messages — never a transaction even though they mention amounts. */
    private val OTP_PATTERN = Regex(
        "\\botp\\b|one\\s*time\\s*password|verification\\s*code|do\\s*not\\s*share",
        RegexOption.IGNORE_CASE
    )

    /** Promotional / marketing: offers, pre-approved loans, apply-now, cashback offers. */
    private val PROMO_PATTERN = Regex(
        "pre-?approved|apply\\s+now|click\\s+here|click\\s+below|exclusive\\s+offer|" +
        "limited\\s+period|t&c\\s+apply|terms\\s+(and|&)\\s+conditions\\s+apply|" +
        "get\\s+(up\\s+to|flat)|earn\\s+(up\\s+to|flat)|win\\s+(up\\s+to|flat)|" +
        "lowest\\s+rates|attractive\\s+rates|attractive\\s+interest|" +
        "shop\\s+(now|today)|book\\s+now|grab\\s+(now|the\\s+offer)|hurry|" +
        "convert\\s+(to|into)\\s+emi|convert\\s+your\\s+purchase",
        RegexOption.IGNORE_CASE
    )

    /** Balance-enquiry-only SMS: no debit/credit verb, just "balance is" content. */
    private val BALANCE_ENQUIRY_PATTERN = Regex(
        "(available|avbl|avl)\\s*(balance|bal)\\s*(in|of|for|as\\s*on|is)",
        RegexOption.IGNORE_CASE
    )

    /** Future/scheduled debit announcement, NOT yet executed. */
    private val FUTURE_DEBIT_PATTERN = Regex(
        "(will\\s+be|shall\\s+be|is\\s+going\\s+to\\s+be|to\\s+be)\\s+" +
        "(debited|deducted|charged|auto.?debited)|" +
        "(auto\\s*pay|auto.?debit|standing\\s*instruction)\\s+(is\\s+)?" +
        "(scheduled|due|reminder|set\\s+up)|" +
        "scheduled\\s+(on|for)|reminder[:\\s]|" +
        "due\\s+for\\s+(payment|auto.?debit)",
        RegexOption.IGNORE_CASE
    )

    /** Failed/declined/reversed transaction. */
    private val FAILED_TXN_PATTERN = Regex(
        "\\b(failed|declined|unsuccessful|could\\s+not\\s+be\\s+processed|not\\s+processed|" +
        "rejected|cancelled|canceled|reversed)\\b",
        RegexOption.IGNORE_CASE
    )

    /** Bill statement keywords — those go to parseBillSms, not parseSms. */
    private val BILL_STATEMENT_PATTERN = Regex(
        "(statement|bill)\\s+(is\\s+)?generated|" +
        "total\\s+amount\\s+due|min(?:imum)?\\s+amount\\s+due|" +
        "total\\s+due[:\\s]|payment\\s+due\\s+on|due\\s+date",
        RegexOption.IGNORE_CASE
    )

    // ---------------------------------------------------------------------------------------
    // 4. Debit / Credit detection
    // ---------------------------------------------------------------------------------------

    /**
     * Words that signal money LEAVING the account / card. Word-boundaried where ambiguous.
     */
    private val DEBIT_KEYWORDS = listOf(
        "debited", "deducted", "spent", "withdrawn", "purchase", "purchased",
        "charged", "charge of", "swiped at", "paid to", "paid for", "payment of",
        "sent rs", "sent inr", "sent ₹", "sent to", "transferred to", "trf to",
        "txn alert", "transaction of", "txn of",
        "emi deducted", "emi of", "emi paid",
        "auto debit", "auto-debit", "nach debit",
        "standing instruction executed", "si executed",
        "pos purchase", "online purchase",
        "wallet debited", "paid from wallet",
        "atm wdl", "atm withdrawal"
    )

    /**
     * Words that signal money ARRIVING in the account / card.
     */
    private val CREDIT_KEYWORDS = listOf(
        "credited", "received", "added to your", "deposited",
        "refund", "refunded", "cashback", "reward credited", "reward of",
        "reversed to", "reversal of", "salary credited", "received from"
    )

    /** Words that, when sitting right next to the amount, strongly anchor the direction. */
    private val DEBIT_ANCHOR_NEAR_AMOUNT = Regex(
        "(?i)(?:debited|deducted|spent|withdrawn|charged|charge\\s+of|paid|sent|" +
        "purchase\\s+of|transaction\\s+of|txn\\s+of|emi\\s+of|payment\\s+of)\\s+" +
        "(?:by|for|of|with|towards|to\\s+a/c)?\\s*" +
        "(?:rs\\.?|inr|₹)?\\s*[\\d,]+(?:\\.\\d{1,2})?"
    )
    private val CREDIT_ANCHOR_NEAR_AMOUNT = Regex(
        "(?i)(?:credited|received|deposited|refunded|refund\\s+of|cashback\\s+of|" +
        "added\\s+to|reversal\\s+of)\\s+" +
        "(?:by|for|of|with|towards|to\\s+a/c)?\\s*" +
        "(?:rs\\.?|inr|₹)?\\s*[\\d,]+(?:\\.\\d{1,2})?"
    )
    private val AMOUNT_ANCHORED_TO_DEBIT = Regex(
        "(?i)(?:rs\\.?|inr|₹)\\s*[\\d,]+(?:\\.\\d{1,2})?\\s+" +
        "(?:has\\s+been\\s+|is\\s+|was\\s+)?" +
        "(?:debited|deducted|spent|charged|paid|withdrawn)"
    )
    private val AMOUNT_ANCHORED_TO_CREDIT = Regex(
        "(?i)(?:rs\\.?|inr|₹)\\s*[\\d,]+(?:\\.\\d{1,2})?\\s+" +
        "(?:has\\s+been\\s+|is\\s+|was\\s+)?" +
        "(?:credited|received|refunded|deposited)"
    )

    private enum class Direction { DEBIT, CREDIT, UNKNOWN }

    /** Pick the direction with the strongest signal. */
    private fun detectDirection(sms: String): Direction {
        val lower = sms.lowercase()

        // Explicit "payment ... received" / "payment received towards ..." is always CREDIT
        // (typical for credit-card bill payment receipt SMSes).
        if (Regex("\\bpayment\\s+(?:of\\s+[^.;]{1,40}\\s+)?received\\b").containsMatchIn(lower) ||
            Regex("\\breceived\\s+towards\\b").containsMatchIn(lower)) {
            return Direction.CREDIT
        }

        val hasStrongDebitVerb = Regex(
            "\\b(spent|debited|deducted|withdrawn|charged|charge\\s+of|paid\\s+to|paid\\s+from)\\b"
        ).containsMatchIn(lower)

        // Strong refund/reversal/credit override: only when there is NO competing strong
        // debit verb (otherwise "You've spent Rs.1500 ... earned cashback of Rs.30" would
        // wrongly flip to CREDIT).
        if (!hasStrongDebitVerb &&
            Regex("\\b(refund|refunded|reversal|reversed|cashback)\\b").containsMatchIn(lower) &&
            Regex("\\b(credited|received|added|earned)\\b").containsMatchIn(lower)) {
            return Direction.CREDIT
        }

        val debitHits = DEBIT_KEYWORDS.count { lower.contains(it) }
        val creditHits = CREDIT_KEYWORDS.count { lower.contains(it) }

        if (debitHits > creditHits) return Direction.DEBIT
        if (creditHits > debitHits) return Direction.CREDIT

        // Tie / both-zero: see which verb sits directly next to an amount.
        val debitAnchored =
            DEBIT_ANCHOR_NEAR_AMOUNT.containsMatchIn(sms) || AMOUNT_ANCHORED_TO_DEBIT.containsMatchIn(sms)
        val creditAnchored =
            CREDIT_ANCHOR_NEAR_AMOUNT.containsMatchIn(sms) || AMOUNT_ANCHORED_TO_CREDIT.containsMatchIn(sms)

        return when {
            debitAnchored && !creditAnchored -> Direction.DEBIT
            creditAnchored && !debitAnchored -> Direction.CREDIT
            debitAnchored && creditAnchored -> Direction.DEBIT  // money-out wins ties
            else -> Direction.UNKNOWN
        }
    }

    // ---------------------------------------------------------------------------------------
    // 4b. Transfer detection (self-transfers & credit-card bill payments)
    // ---------------------------------------------------------------------------------------

    /**
     * Credit-card bill payments must never count as spend OR income:
     *   - The bank-side DEBIT ("Rs.X paid towards your credit card") is just settling
     *     purchases that were ALREADY counted when they happened on the card.
     *   - The card-side CREDIT ("payment received towards your card") reduces the card's
     *     outstanding, it is not money earned.
     * Counting either would double-count. We tag both as transfers.
     */
    private val CREDIT_CARD_PAYMENT_PATTERN = Regex(
        "(?i)(" +
        "payment\\s+of\\s+(?:rs\\.?|inr|₹)?\\s*[\\d,]+(?:\\.\\d{1,2})?\\s+(?:has\\s+been\\s+)?received\\s+(?:on|towards|for|against)\\s+(?:your\\s+)?(?:credit\\s*card|card)" +
        "|payment\\s+(?:has\\s+been\\s+)?received\\s+(?:on|towards|for|against)\\s+(?:your\\s+)?(?:credit\\s*card|card)" +
        "|received\\s+towards\\s+(?:your\\s+)?(?:credit\\s*card|card)\\s+(?:bill|payment|dues|outstanding|ending)" +
        "|towards\\s+(?:your\\s+)?credit\\s*card\\s+(?:bill|payment|dues|outstanding)" +
        "|paid\\s+towards\\s+(?:your\\s+)?credit\\s*card" +
        "|credit\\s*card\\s+(?:bill\\s+)?payment\\s+(?:of|received|done|successful|is\\s+successful)" +
        ")"
    )

    /** Explicit own-account / self transfers (money never leaves the user). */
    private val SELF_TRANSFER_PATTERN = Regex(
        "(?i)(" +
        "transfer(?:red)?\\s+to\\s+your\\s+own\\b|" +
        "self[-\\s]transfer|" +
        "to\\s+your\\s+own\\s+(?:account|a/c|savings)|" +
        "moved\\s+to\\s+your\\s+own" +
        ")"
    )

    private fun detectTransfer(sms: String): Boolean =
        CREDIT_CARD_PAYMENT_PATTERN.containsMatchIn(sms) || SELF_TRANSFER_PATTERN.containsMatchIn(sms)

    /** Public detector used to re-tag already-stored transactions after an app upgrade. */
    fun isTransferSms(sms: String): Boolean = detectTransfer(sms)

    // ---------------------------------------------------------------------------------------
    // 5. Amount extraction
    // ---------------------------------------------------------------------------------------

    /**
     * Always-strip noise: clauses that NEVER carry the transaction amount.
     * (balance reporting, call/sms tails, outstanding-of side amounts.)
     */
    private val ALWAYS_NOISE_CLAUSES = listOf(
        // available balance / limit reporting (the dominant false-positive)
        Regex("(?i)(available|avbl|avl)\\s*(balance|bal|limit|lmt|credit)[^,.;\\n]{0,60}"),
        Regex("(?i)\\bbal(?:ance)?[:\\s]+(?:rs\\.?|inr|₹)?\\s*[\\d,]+(?:\\.\\d{1,2})?[^,.;\\n]{0,30}"),
        Regex("(?i)\\bnew\\s+bal[:\\s]+(?:rs\\.?|inr|₹)?\\s*[\\d,]+(?:\\.\\d{1,2})?"),
        Regex("(?i)total\\s+avl\\s+bal[^,.;\\n]{0,60}"),
        // "Call 1800..." / "SMS BLOCK 1234 to 5676782" / "Block at hdfcbk.in/sec"
        Regex("(?i)\\bcall\\s+\\d{4,}[^.;\\n]{0,80}"),
        Regex("(?i)\\bsms\\s+block\\s+\\S+\\s+to\\s+\\d+[^.;\\n]{0,80}"),
        Regex("(?i)\\bsms\\s+block\\s+to\\s+\\d+[^.;\\n]{0,80}"),
        Regex("(?i)\\bblock\\s+(at|on)\\s+\\S+"),
        Regex("(?i)\\bnot\\s+you\\??[^.;\\n]{0,80}"),
        // "outstanding of Rs.X"
        Regex("(?i)outstanding\\s+of\\s+(?:rs\\.?|inr|₹)?\\s*[\\d,]+(?:\\.\\d{1,2})?")
    )

    /**
     * Conditional noise: clauses that introduce a SECONDARY amount (cashback earned
     * alongside a purchase). Only strip when the message clearly has another amount
     * with a debit verb — otherwise the cashback amount IS the transaction.
     */
    private val SECONDARY_CASHBACK_CLAUSES = listOf(
        Regex("(?i)(?:you\\s+(?:earned|received|got)\\s+)?(?:cashback|reward(?:s)?|points?)\\s+of\\s+(?:rs\\.?|inr|₹)?\\s*[\\d,]+(?:\\.\\d{1,2})?[^.;\\n]{0,40}"),
        Regex("(?i)earned\\s+(?:rs\\.?|inr|₹)?\\s*[\\d,]+(?:\\.\\d{1,2})?\\s+(?:cashback|reward(?:s)?)")
    )

    /**
     * Strip clauses that introduce non-transaction amounts. For the cashback exception,
     * we only strip when there's clearly a separate debit-verb amount in the body.
     */
    private fun stripAmountNoise(sms: String): String {
        var s = sms
        for (noise in ALWAYS_NOISE_CLAUSES) s = noise.replace(s, " ")

        // Only strip cashback-of-Rs.X if the message contains a spent/debited amount
        // elsewhere (so we don't accidentally drop a pure-cashback credit's amount).
        val hasOtherDebitAnchored = DEBIT_ANCHOR_NEAR_AMOUNT.containsMatchIn(s)
        if (hasOtherDebitAnchored) {
            for (noise in SECONDARY_CASHBACK_CLAUSES) s = noise.replace(s, " ")
        }
        return s
    }

    /**
     * Strongly preferred patterns where the amount sits directly next to a debit/credit verb.
     * Searched first so we ALWAYS pick the transaction amount over any leftover noise.
     */
    private val PRIMARY_AMOUNT_PATTERNS = listOf(
        // "debited for Rs.500" / "debited by 500.00" / "debited Rs 500"
        Regex(
            "(?i)(?:debited|deducted|spent|charged|paid|withdrawn|sent|credited|received|deposited|refunded|refund\\s+of)\\s+" +
            "(?:by|for|of|with|amount(?:\\s+of)?|towards|to\\s+a/c)?\\s*" +
            "(?:rs\\.?|inr|₹)?\\s*([\\d,]+(?:\\.\\d{1,2})?)"
        ),
        // "Rs.500 debited" / "Rs 500.00 spent" / "INR 1,499.00 has been debited"
        Regex(
            "(?i)(?:rs\\.?|inr|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)\\s+" +
            "(?:has\\s+been\\s+|is\\s+|was\\s+)?" +
            "(?:debited|deducted|spent|charged|paid|withdrawn|sent|credited|received|deposited|refunded)"
        ),
        // "Sent Rs.450 from..." / "Charge of INR 5,500 on..."
        Regex(
            "(?i)(?:sent|charge\\s+of|payment\\s+of|transaction\\s+of|txn\\s+of|emi\\s+of)\\s+" +
            "(?:rs\\.?|inr|₹)?\\s*([\\d,]+(?:\\.\\d{1,2})?)"
        )
    )

    /** Generic fallback patterns — used only when primary patterns don't fire. */
    private val FALLBACK_AMOUNT_PATTERNS = listOf(
        Regex("(?i)(?:rs\\.?|inr|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)"),
        Regex("(?i)\\b([\\d,]+(?:\\.\\d{1,2})?)\\s*(?:rs|inr|₹|rupees)\\b")
    )

    /**
     * Parse an amount string that may use Indian lakh notation ("1,25,000.00") OR western
     * thousands ("1,500.00"). Both are valid; just strip commas.
     */
    private fun parseAmountString(raw: String): Double? {
        return try {
            val v = raw.replace(",", "").toDouble()
            if (v > 0 && v < 10_000_000) v else null
        } catch (_: Exception) { null }
    }

    private fun extractAmount(sms: String): Double? {
        val cleaned = stripAmountNoise(sms)
        for (p in PRIMARY_AMOUNT_PATTERNS) {
            p.find(cleaned)?.let { m ->
                parseAmountString(m.groupValues[1])?.let { return it }
            }
        }
        for (p in FALLBACK_AMOUNT_PATTERNS) {
            p.find(cleaned)?.let { m ->
                parseAmountString(m.groupValues[1])?.let { return it }
            }
        }
        return null
    }

    // ---------------------------------------------------------------------------------------
    // 6. Merchant extraction
    // ---------------------------------------------------------------------------------------

    private val MERCHANT_PATTERNS_FOR_DEBIT = listOf(
        // "at AMAZON RETAIL on" / "at SWIGGY"
        Regex("(?i)\\bat\\s+([A-Z0-9][A-Za-z0-9&._'\\- ]{1,45}?)(?=\\s+(?:on|for|via|ref|trxn|upi|dated|by|with|using)\\b|[.,;]|$)"),
        // UPI multi-segment: "UPI/P2A/<refnum>/<MERCHANT>" or "UPI/MERCHANT/<refnum>/<MERCHANT>"
        // The merchant is the LAST segment after the last slash (before ./,/space at end).
        Regex("(?i)\\bupi[/:\\-]\\S*?[/]([A-Z][A-Za-z0-9&._' ]{1,45}?)(?=\\s+(?:bal|on|via|ref|dated|by|upi)\\b|[.,;]|\\s*$)"),
        // "UPI ZOMATO" / "UPI:412345/SWIGGY" — bare UPI followed by merchant word
        Regex("(?i)\\bupi[\\s:]+([A-Z][A-Za-z0-9&._' ]{2,45}?)(?=\\s+(?:bal|on|via|ref|dated|by)\\b|[.,;]|\\s*$)"),
        // "Sent Rs.99 from ... to rahul@oksbi" — UPI VPA handle
        Regex("(?i)\\b(?:to|paid\\s+to|sent\\s+to)\\s+(?:verified\\s+upi\\s+id\\s+)?([a-z0-9._\\-]+@[a-z][a-z0-9.\\-]+)\\b"),
        // "to RAHUL KUMAR on" / "trf to BUILDER PVT LTD"
        Regex("(?i)\\b(?:paid\\s+to|sent\\s+to|trf\\s+to|transfer(?:red)?\\s+to|to)\\s+([A-Z][A-Za-z0-9&._'\\- ]{1,45}?)(?=\\s+(?:on|for|via|ref|dated|by|upi|with|using)\\b|[.,;]|$)"),
        // "swiped at SHOP"
        Regex("(?i)\\bswiped\\s+at\\s+([A-Z0-9][A-Za-z0-9&._'\\- ]{1,45}?)(?=\\s+on|[.,;]|$)"),
        // "towards SIP of PARAG PARIKH" / "towards NETFLIX"
        Regex("(?i)\\btowards\\s+(?:sip\\s+of\\s+)?([A-Z0-9][A-Za-z0-9&._'\\- ]{2,45}?)(?=\\s+(?:via|on|ref|by)\\b|[.,;]|$)"),
        // Axis style: "Spent Card no. XX1234 INR 1850 ZEPTO MARKETPLACE 11-05-26"
        // After an INR/Rs amount, capture an ALL-CAPS merchant chunk before the date.
        Regex("(?i)(?:rs\\.?|inr|₹)\\s*[\\d,]+(?:\\.\\d{1,2})?\\s+([A-Z][A-Z0-9&._' ]{2,45}?)(?=\\s+\\d{1,4}[-/]\\d{1,2}[-/]\\d{2,4}|[.,;]|$)"),
        // "; MERCHANT credited" (ICICI style — money sent, merchant credited)
        Regex("(?i)[;]\\s*([A-Z][A-Z0-9&._' ]{2,45}?)\\s+credited\\b"),
        // Last resort: "for <PURPOSE>" — only when capture starts with a non-digit
        Regex("(?i)\\bfor\\s+([A-Za-z][A-Za-z0-9&._'\\- ]{2,40}?)(?=\\s+(?:on|via|ref|by)\\b|[.,;]|$)")
    )

    private val MERCHANT_PATTERNS_FOR_CREDIT = listOf(
        // "from AMAZON" — refund source / sender
        Regex("(?i)\\bfrom\\s+([A-Z0-9][A-Za-z0-9&._'\\- ]{2,45}?)(?=\\s+(?:on|via|ref|dated)\\b|[.,;]|$)"),
        // "received from RAHUL" / "salary from ACME"
        Regex("(?i)\\b(?:received\\s+from|salary\\s+from|sal\\s+from)\\s+([A-Z0-9][A-Za-z0-9&._'\\- ]{2,45}?)(?=\\s+(?:on|via|ref|dated)\\b|[.,;]|$)"),
        // "by IMPS/RAHUL KUMAR/412345" / "by NEFT-SALARY-ACME" — handle / and - separators
        Regex("(?i)\\bby\\s+(?:neft|rtgs|imps|upi)[/:\\-]\\s*([A-Z][A-Za-z0-9&._'\\- ]{2,45}?)(?=\\s+(?:on|via|ref|dated)\\b|[.,;/]|$)"),
        // "credited by ACME" — without explicit method
        Regex("(?i)\\bby\\s+([A-Z][A-Za-z0-9&._'\\- ]{2,45}?)(?=\\s+(?:on|via|ref|dated)\\b|[.,;]|\\(|$)")
    )

    /** Stopwords/junk that should never be returned as a merchant name. */
    private val MERCHANT_STOPWORDS = setOf(
        "your", "the", "a", "an", "is", "on", "via", "ref", "info", "alert", "you",
        "this", "that", "txn", "transaction", "payment", "details", "card", "account",
        "acc", "acct", "a/c", "upi", "ref no", "ref:", "amount", "balance",
        "available", "avbl", "avl", "limit", "imps", "neft", "rtgs", "nach", "ref.",
        "transaction details", "see details", "tap to view", "not you", "not done",
        "do not share", "click here", "apply now", "report", "block", "dispute",
        "verified upi id", "your card", "your a/c", "your account"
    )

    /** Strip ref numbers, dates, UPI refs, "Not you?", trailing helpline numbers. */
    private fun cleanMerchant(raw: String): String {
        var m = raw.trim()
        // Strip leading "your" / "the"
        m = m.replace(Regex("(?i)^(your|the)\\s+"), "")
        // Strip trailing "on dd-mm-yy" or "on dd/mm/yyyy" or "dd-MMM-yyyy"
        m = m.replace(Regex("(?i)\\s+on\\s+\\d{1,4}[-/]\\d{1,2}[-/]\\d{2,4}.*$"), "")
        m = m.replace(Regex("(?i)\\s+\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4}.*$"), "")
        m = m.replace(Regex("(?i)\\s+\\d{1,2}[-\\s][A-Za-z]{3,9}[-\\s]\\d{2,4}.*$"), "")
        // Strip "Ref ..." tail
        m = m.replace(Regex("(?i)\\s+ref\\.?\\s*(no\\.?)?\\s*\\S+.*$"), "")
        m = m.replace(Regex("(?i)\\s+trxn\\s*#?\\s*\\S+.*$"), "")
        // Strip "UPI Ref ..." tail
        m = m.replace(Regex("(?i)\\s+upi\\s*ref.*$"), "")
        // Strip "Not you" tail
        m = m.replace(Regex("(?i)\\s*not\\s+you\\??.*$"), "")
        // Strip trailing "Avl bal..." (defensive: should already be stripped)
        m = m.replace(Regex("(?i)\\s+(avl|available)\\s+(bal|balance|limit|lmt).*$"), "")
        // Strip standalone trailing digit-only words (likely refnums/PIN segments)
        m = m.replace(Regex("\\s+\\d{4,}\\s*$"), "")
        // Collapse whitespace
        m = m.replace(Regex("\\s+"), " ").trim()
        // Trim trailing/leading punctuation
        m = m.trim('.', ',', ';', ':', '-', '/')
        return m
    }

    private val FINANCIAL_GUARDRAIL_HINT = Regex(
        "INDIAN CLEARING|CLEARING CORP|CORPORATION|NPS TRUST|CDSL|NSDL|MUTUAL FUND|SIP",
        RegexOption.IGNORE_CASE
    )

    private fun extractMerchant(sms: String, direction: Direction): String {
        // Apply the same noise stripping as for amount extraction — keeps merchant patterns
        // from latching onto "Call 1800... to report" / "SMS BLOCK ... to 5676782".
        val cleaned = stripAmountNoise(sms)
        val patterns = if (direction == Direction.CREDIT) {
            MERCHANT_PATTERNS_FOR_CREDIT + MERCHANT_PATTERNS_FOR_DEBIT
        } else {
            MERCHANT_PATTERNS_FOR_DEBIT
        }
        for (p in patterns) {
            p.find(cleaned)?.let { match ->
                val candidate = cleanMerchant(match.groupValues[1])
                if (candidate.length >= 2 &&
                    candidate.lowercase() !in MERCHANT_STOPWORDS &&
                    !candidate.matches(Regex("[\\d\\s.,-]+")) &&
                    !candidate.matches(Regex("(?i)rs\\.?\\s*\\d.*"))) {
                    return candidate
                }
            }
        }
        FINANCIAL_GUARDRAIL_HINT.find(cleaned)?.let { m ->
            val end = minOf(cleaned.length, m.range.last + 15)
            return cleaned.substring(m.range.first, end).split(Regex("[.,]"))[0].trim()
        }
        return "Unknown"
    }

    // ---------------------------------------------------------------------------------------
    // 7. Last-4 digits & source-info
    // ---------------------------------------------------------------------------------------

    /**
     * Anchored to "card"/"a/c"/"account"/"ending in" so we don't accidentally grab a 4-digit
     * reference number. Returns just the digits, no "XX" prefix.
     */
    private fun extractDigits(sms: String): String? {
        val anchored = Regex(
            "(?i)(?:card|a/c|acc(?:t|ount)?|ending\\s+in)\\s*(?:no\\.?|number)?\\s*[:#]?\\s*[Xx*]{0,12}(\\d{3,4})"
        ).find(sms)?.groupValues?.get(1)
        return anchored
    }

    private fun buildSourceInfo(sms: String, senderId: String): String {
        val paymentType = detectPaymentType(sms)
        val bank = bankNameFromSender(senderId)
        val digits = extractDigits(sms)
        // Refine "Credit Card" vs "Debit Card" for known bank
        val refinedType = when {
            CREDIT_CARD_PATTERN.containsMatchIn(sms) -> "Credit Card"
            DEBIT_CARD_PATTERN.containsMatchIn(sms) -> "Debit Card"
            else -> paymentType
        }
        return when {
            bank != null && digits != null -> "$bank ($digits) • $refinedType"
            bank != null -> "$bank • $refinedType"
            digits != null -> "$refinedType ($digits)"
            else -> refinedType
        }
    }

    // ---------------------------------------------------------------------------------------
    // 8. Credit-card bill parsing
    // ---------------------------------------------------------------------------------------

    private val BILL_TRIGGER_PATTERN = Regex(
        "(statement|bill)\\s+(is\\s+)?generated|statement\\s+for\\s+your|" +
        "total\\s+amount\\s+due|min(?:imum)?\\s+amount\\s+due",
        RegexOption.IGNORE_CASE
    )

    // "Total Amount Due" / "Total Due" / "Amount Due" — but explicitly NOT preceded by
    // "min", "minimum", "last", "previous", or "past" within ~3 words.
    private val TOTAL_DUE_PATTERN = Regex(
        "(?i)(?<!min\\s)(?<!minimum\\s)(?<!last\\s)(?<!previous\\s)(?<!past\\s)" +
        "(?:total\\s+(?:amount\\s+)?due|total\\s+due|(?<!min\\s)(?<!minimum\\s)amount\\s+due)" +
        "[:\\s]+(?:rs\\.?|inr|₹)?\\s*([\\d,]+(?:\\.\\d{1,2})?)"
    )

    private val MIN_DUE_PATTERN = Regex(
        "(?i)(?:min(?:imum)?\\s+(?:amount\\s+)?due)[:\\s]+(?:rs\\.?|inr|₹)?\\s*([\\d,]+(?:\\.\\d{1,2})?)"
    )

    private val DUE_DATE_PATTERN = Regex(
        "(?i)(?:due\\s+(?:on|date|dt)[:\\s]+|payment\\s+due\\s+(?:on|by)[:\\s]+|by\\s+)" +
        "(\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4}|\\d{1,2}[-\\s][A-Za-z]{3,9}[-\\s]\\d{2,4})"
    )

    private fun parseDueDate(dateStr: String): Long? {
        val formats = listOf(
            "dd-MM-yy", "dd-MM-yyyy", "dd/MM/yy", "dd/MM/yyyy",
            "d-M-yy", "d-M-yyyy", "d/M/yy", "d/M/yyyy",
            "dd-MMM-yy", "dd-MMM-yyyy", "dd MMM yy", "dd MMM yyyy",
            "d MMM yy", "d MMM yyyy"
        )
        for (f in formats) {
            try {
                val sdf = java.text.SimpleDateFormat(f, java.util.Locale.ENGLISH)
                sdf.isLenient = false
                sdf.parse(dateStr)?.let { return it.time }
            } catch (_: Exception) { /* try next */ }
        }
        return null
    }

    fun parseBillSms(body: String, address: String, dateMills: Long): com.example.smsaggregator.data.CreditCardBill? {
        if (!BILL_TRIGGER_PATTERN.containsMatchIn(body)) return null

        val totalDue = TOTAL_DUE_PATTERN.find(body)?.groupValues?.get(1)?.let { parseAmountString(it) }
            ?: return null
        val minDue = MIN_DUE_PATTERN.find(body)?.groupValues?.get(1)?.let { parseAmountString(it) } ?: 0.0
        val dueDateStr = DUE_DATE_PATTERN.find(body)?.groupValues?.get(1) ?: return null
        val dueDate = parseDueDate(dueDateStr) ?: return null

        val senderId = address.trim()
        val bankName = bankNameFromSender(senderId) ?: "Unknown Bank"
        val digits = extractDigits(body)?.let { "XX$it" } ?: "XXXX"

        return com.example.smsaggregator.data.CreditCardBill(
            bankName = bankName,
            cardDigits = digits,
            totalDue = totalDue,
            minDue = minDue,
            dueDate = dueDate,
            rawSms = body,
            billGeneratedDate = dateMills
        )
    }

    // ---------------------------------------------------------------------------------------
    // 9. Main parse entry point
    // ---------------------------------------------------------------------------------------

    fun parseSms(body: String, address: String, dateMills: Long): Transaction? {
        val senderId = address.trim()

        // ----- STAGE A: sender gatekeeper -----
        if (senderId.isNotEmpty()) {
            // Reject personal phone numbers outright (a friend texting "I paid Rs.500" must
            // never become a transaction).
            if (isPhoneNumberSender(senderId)) return null
            // Reject promotional category senders even if the entity is a real bank.
            if (isPromoCategorySender(senderId)) return null
            // For DLT-formatted senders, require the entity to be in our trusted list.
            // For other formats (rare), fall through to content checks.
            if (DLT_SENDER.matches(normalizeSender(senderId).replace(" ", ""))) {
                if (!isTrustedSender(senderId)) return null
            }
        }

        // ----- STAGE B: content rejection -----
        // Bill statements: route to parseBillSms, not parseSms.
        if (BILL_STATEMENT_PATTERN.containsMatchIn(body)) return null
        // OTP messages are never transactions.
        if (OTP_PATTERN.containsMatchIn(body)) return null
        // Promotional content (apply now, pre-approved, etc.).
        if (PROMO_PATTERN.containsMatchIn(body)) return null
        // Future / scheduled debits.
        if (FUTURE_DEBIT_PATTERN.containsMatchIn(body)) return null
        // Failed / declined / reversed transactions.
        // Exception: a message that says e.g. "Rs.500 has been refunded and credited" is a
        // genuine refund credit. We only bypass FAILED_TXN_PATTERN when the credit/refund verb
        // is in the PAST tense / completed form, not a future/conditional ("will be refunded",
        // "if debited, will be refunded", "amount will be reversed").
        if (FAILED_TXN_PATTERN.containsMatchIn(body)) {
            val hasCompletedCredit = Regex(
                "(?i)\\b(has\\s+been\\s+(credited|refunded|reversed)|" +
                "(credited|refunded|reversed)\\s+to\\s+(your|the)\\s+(a/c|account|card|wallet))\\b"
            ).containsMatchIn(body)
            val hasFutureCredit = Regex(
                "(?i)\\b(will\\s+be|shall\\s+be|to\\s+be|if\\s+debited)\\s+(refunded|credited|reversed)\\b"
            ).containsMatchIn(body)
            if (!hasCompletedCredit || hasFutureCredit) return null
        }
        // Balance enquiry SMS with no debit/credit verb at all.
        if (BALANCE_ENQUIRY_PATTERN.containsMatchIn(body) &&
            DEBIT_KEYWORDS.none { body.contains(it, ignoreCase = true) } &&
            CREDIT_KEYWORDS.none { body.contains(it, ignoreCase = true) }) {
            return null
        }

        // ----- STAGE C: direction -----
        val direction = detectDirection(body)
        if (direction == Direction.UNKNOWN) return null
        val txnType = if (direction == Direction.DEBIT) TransactionType.DEBIT else TransactionType.CREDIT

        // ----- STAGE C2: transfer vs spend/income -----
        // Self-transfers and credit-card bill payments move money you already own (or
        // already counted) — flag them so they're excluded from both spend and income.
        val isTransfer = detectTransfer(body)
        val isRefundCredit = direction == Direction.CREDIT &&
            com.example.smsaggregator.data.MoneyFlow.isRefundText(body)

        // ----- STAGE D: amount -----
        val amount = extractAmount(body) ?: return null

        // ----- STAGE E: merchant -----
        val merchant = extractMerchant(body, direction)

        // ----- STAGE F: source -----
        val sourceInfo = buildSourceInfo(body, senderId)

        // ----- STAGE G: category -----
        // - Transfers get a dedicated "Transfer" bucket.
        // - Pure credits (salary, interest, money received) are "Income".
        // - Refund credits KEEP their merchant-based category so they net against the
        //   original purchase's category; debits categorize normally.
        val category = when {
            isTransfer -> "Transfer"
            direction == Direction.CREDIT && !isRefundCredit -> "Income"
            else -> Categorizer.categorize(body, merchant, sourceInfo)
        }

        return Transaction(
            amount = amount,
            merchant = merchant,
            category = category,
            date = dateMills,
            type = txnType,
            source = sourceInfo,
            rawSms = body,
            isTransfer = isTransfer,
            updatedAt = dateMills
        )
    }

    /** Legacy entry point — kept for any callers that don't have the sender address. */
    fun parseSms(body: String, dateMills: Long): Transaction? = parseSms(body, "", dateMills)
}

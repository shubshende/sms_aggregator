package com.example.smsaggregator.logic

object Categorizer {

    private val CATEGORIES = mapOf(
        // Investments — moved to top for priority, renaming to plural
        "Investments" to Regex("sip|mutual fund|zerodha|groww|upstox|smallcase|kuvera|coin|ppf|nps|gold bond|fd |fixed deposit|indian clearing|indian clg|clearing corp|clearing corporation|icc |iccl|cdsl|nsdl|bse|nse|demat|dsp|axis mutual|hdfc mutual|icici mutual|sbi mutual|nippon|birla|kotak mutual|parag parikh|motilal|tata mutual|uti mutual|angel one|5paisa|mirae|franklin|sundaram|invesco|edelweiss|digilocker|cams|kfintech|mf utility|trading|stock broker|stock market|securities|equity|shares|\\bscf\\b|investment|wealth|investor", RegexOption.IGNORE_CASE),

        // Gold & Jewellery — moved to second priority
        "Gold & Jewellery" to Regex("_?p.?n.?gadgil|png gadgil|png jewel|tanishq|kalyan jewel|malabar gold|joyalukkas|senco gold|pc jeweller|tribhovandas|tbz |grt jewel|lalitha jewel|jos alukkas|bluestone|caratlane|candere|jewel|gold|diamond|platinum|bullion|waman hari|orra |tata gold|augmont|safegold|jewellers|jeweller|ornament|silver|solitaire", RegexOption.IGNORE_CASE),

        // Food & Dining — moved down and tightened with word boundaries for broad terms
        "Food & Dining" to Regex("zomato|swiggy|bundl|restaurant|\\bcafe\\b|domino|mcdonald|mcd |burger king|pizza hut|pizza|kfc|subway|barbeque|biryani|dhaba|\\bhotel\\b|bistro|\\bfood\\b|eatery|starbucks|haldiram|sagar ratna|saravana|paradise|bikanervala|behrouz|faasos|box8|rebel foods|eat club|freshmenu|lunchbox|chaayos|chai point|wow momo|baskin robbins|ice cream|bakers|bakery|sweet|idly", RegexOption.IGNORE_CASE),
        
        // Groceries
        "Groceries" to Regex("bigbasket|blinkit|zepto|grofers|dmart|d-mart|reliance fresh|reliance smart|nature.?s basket|jiomart|supr daily|milkbasket|spencers|more\\.com|more supermarket|star bazaar|easyday|spar|le marche|ratnadeep|metro cash|walmart", RegexOption.IGNORE_CASE),
        
        // Fuel
        "Fuel" to Regex("petrol|hp pump|indian oil|iocl|hpcl|bpcl|bharat petroleum|hindustan petroleum|essar|nayara|shell|fuel|cng|gas station|filling station", RegexOption.IGNORE_CASE),
        
        // Shopping
        "Shopping" to Regex("amazon|flipkart|myntra|ajio|nykaa|meesho|shopclues|tatacliq|snapdeal|limeroad|bewakoof|clovia|firstcry|hopscotch|croma|reliance digital|vijay sales|ezone|poorvika|chroma|decathlon|pepperfry|urban ladder|ikea|home centre|shoppers stop|lifestyle|pantaloons|westside|max fashion|zara|h&m|marks spencer|fabindia|w for woman|biba|ethnicity|lenskart|titan eye|mama earth|sugar cosmetics|purplle|plum|noise|boat|crossword|archies|hallmark", RegexOption.IGNORE_CASE),
        
        // Travel
        "Travel" to Regex("ola|uber|rapido|irctc|railways|metro|redbus|yatra|makemytrip|goibibo|ease my trip|cleartrip|airport|indigo|air india|spicejet|vistara|akasa|go first|air asia|mmt|oyo|treebo|fab hotel|zostel|taj hotel|itc hotel|lemon tree|ginger hotel|absher|bus|flight", RegexOption.IGNORE_CASE),
        
        // Utilities
        "Utilities" to Regex("electricity|bescom|msedcl|water bill|piped gas|mahanagar gas|indraprastha gas|bses|tata power|adani electricity|adani gas|bill payment|torrent power|cesc|calcutta electric|reliance energy|kseb|tneb|wbsedcl|uhbvn|jvvnl|pgvcl", RegexOption.IGNORE_CASE),
        
        // Telecom
        "Telecom" to Regex("airtel|jio|\\bvi\\b|vodafone|bsnl|mtnl|recharge|postpaid|broadband|fiber|dth|tata sky|dish tv|sun direct|d2h|airtel xstream|jio fiber|act fibernet|hathway|tikona|excitel|you broadband", RegexOption.IGNORE_CASE),
        
        // Entertainment
        "Entertainment" to Regex("netflix|hotstar|spotify|prime video|bookmyshow|pvr|inox|zee5|sonyliv|voot|aha|mxplayer|gaana|wynk|youtube premium|disney|apple tv|jio cinema|lionsgate|apple music|paytm movie|event|concert|gaming|steam|playstation|xbox|epic games|google play", RegexOption.IGNORE_CASE),
        
        // Health & Medical
        "Health" to Regex("pharmacy|medplus|apollo|1mg|netmeds|pharmeasy|hospital|clinic|doctor|diagnostic|thyrocare|lal path|healthians|dental|optician|dr\\.?\\s|med |medicine|pathology|radiology|manipal|fortis|max hospital|narayana|aster |aiims|kims|srl diagnostic", RegexOption.IGNORE_CASE),
        
        // Fitness
        "Fitness" to Regex("gym|cult\\.?fit|anytime fitness|gold.?s gym|fitness first|crossfit|yoga|pilates|fitpass|fitternity|muscleblaze|healthkart", RegexOption.IGNORE_CASE),
        
        // Education
        "Education" to Regex("school fee|college fee|tuition|byjus|unacademy|vedantu|coursera|udemy|upgrad|whitehat|skill|exam fee|coaching|physics wallah|allen|aakash|fiitjee|khan academy|simplilearn|great learning|scaler|coding ninjas|linkedin learning", RegexOption.IGNORE_CASE),
        
        // Insurance
        "Insurance" to Regex("lic|policy|premium|insurance|max life|hdfc life|icici prudential|bajaj allianz|star health|care health|digit insurance|acko|policy.?bazaar|niva bupa|aditya birla health|tata aia|sbi life|kotak life", RegexOption.IGNORE_CASE),
        
        // EMI / Loan
        "EMI / Loan" to Regex("emi|loan|equated|repayment|moratorium|foreclosure|bounce charge|penal", RegexOption.IGNORE_CASE),
        
        // Rent
        "Rent" to Regex("rent|house rent|pg rent|accommodation|nobroker|magicbricks|99acres|housing\\.com|flat rent|society maintenance|maintenance charge", RegexOption.IGNORE_CASE),
        
        // Auto Debit / NACH
        "Auto Debit" to Regex("nach|auto.?debit|standing instruction|mandate|si collection", RegexOption.IGNORE_CASE),
        
        // UPI Transfer
        "UPI Transfer" to Regex("upi|imps|neft|rtgs|trf to|transfer to|money sent|phonepe|gpay|bhim", RegexOption.IGNORE_CASE),
        
        // Wallet Topup
        "Wallet Topup" to Regex("wallet load|add money|topup|paytm add|mobikwik add|freecharge add", RegexOption.IGNORE_CASE),
        
        // ATM
        "ATM" to Regex("atm|cash withdrawal|cash wtd", RegexOption.IGNORE_CASE),
        
        // Government
        "Government" to Regex("challan|traffic fine|govt|gst|municipal|bbmp|mcgm|nmmc|income tax|tds|epfo|pf |provident fund|passport|stamp duty|registration fee|court fee|mca fee", RegexOption.IGNORE_CASE),
        
        // Home & Lifestyle
        "Home & Lifestyle" to Regex("home depot|home centre|@home|urban ladder|pepperfry|ikea|godrej interio|sleepwell|nilkamal|asian paints|berger paints|nerolac|pidilite|plumber|electrician|carpenter|pest control|cleaning", RegexOption.IGNORE_CASE)
    )


    // In-memory cache of user/Gemini overrides loaded from Room DB
    private val overrides = mutableMapOf<String, String>()

    fun loadOverrides(overrideMap: Map<String, String>) {
        overrides.clear()
        overrides.putAll(overrideMap)
    }

    fun addOverride(merchantKey: String, category: String) {
        val cleanKey = merchantKey.lowercase().replace("[^a-z0-9_]".toRegex(), "").trim()
        overrides[cleanKey] = category
    }

    fun categorize(sms: String, merchant: String, paymentType: String): String {
        val lowerMerchant = merchant.lowercase()
        
        // 1. Check self-learning overrides FIRST (User Learning takes top priority)
        val key = lowerMerchant.replace("[^a-z0-9_]".toRegex(), "").trim()
        if (key != "unknown" && key.isNotEmpty()) {
            overrides[key]?.let { return it }
        }

        // 2. Fall back to regex patterns (Speed & Efficiency)
        val text = "$sms $merchant $paymentType"
        for ((category, pattern) in CATEGORIES) {
            if (pattern.containsMatchIn(text)) return category
        }
        return "Other"
    }

    // Retain legacy override
    fun categorize(merchant: String): String {
        return categorize("", merchant, "")
    }
}

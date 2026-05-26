package com.example

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class Festival(
    val date: LocalDate,
    val nameEn: String,
    val nameOr: String,
    val isMajor: Boolean = false
)

data class OdiaMonthInfo(
    val nameEn: String,
    val nameOr: String
)

data class RashiInfo(
    val nameEn: String,
    val nameOr: String,
    val symbol: String
)

data class TithiInfo(
    val nameEn: String,
    val nameOr: String,
    val value: Int
)

data class PakshaInfo(
    val nameEn: String,
    val nameOr: String
)

data class OdiaDayInfo(
    val date: java.time.LocalDate,
    val odiaMonth: OdiaMonthInfo,
    val tithi: TithiInfo,
    val paksha: PakshaInfo,
    val rashi: RashiInfo,
    val festivals: List<Festival>
)

object OdiaCalendarData {

    // Helper to translate Gregorian digits to Odia digits
    fun toOdiaDigits(number: String): String {
        return number.map { char ->
            when (char) {
                '0' -> '୦'
                '1' -> '୧'
                '2' -> '୨'
                '3' -> '୩'
                '4' -> '୪'
                '5' -> '୫'
                '6' -> '୬'
                '7' -> '୭'
                '8' -> '୮'
                '9' -> '୯'
                else -> char
            }
        }.joinToString("")
    }

    fun toOdiaDigits(number: Int): String {
        return toOdiaDigits(number.toString())
    }

    // Traditional Odia Months and English translation pairs
    private val odiaMonths = listOf(
        OdiaMonthInfo("Baisakha", "ବୈଶାଖ"),
        OdiaMonthInfo("Jyestha", "ଜ୍ୟେଷ୍ଠ"),
        OdiaMonthInfo("Ashadha", "ଆଷାଢ଼"),
        OdiaMonthInfo("Shrabana", "ଶ୍ରାବଣ"),
        OdiaMonthInfo("Bhadraba", "ଭାଦ୍ରବ"),
        OdiaMonthInfo("Aswina", "ଆଶ୍ୱିନ"),
        OdiaMonthInfo("Kartika", "କାର୍ତ୍ତିକ"),
        OdiaMonthInfo("Margasira", "ମାର୍ଗଶିର"),
        OdiaMonthInfo("Pausa", "ପୌଷ"),
        OdiaMonthInfo("Magha", "ମାଘ"),
        OdiaMonthInfo("Phalguna", "ଫାଲ୍‌ଗୁନ"),
        OdiaMonthInfo("Chaitra", "ଚୈତ୍ର")
    )

    private val rashis = listOf(
        RashiInfo("Mesa (Aries)", "ମେଷ", "♈"),
        RashiInfo("Brisha (Taurus)", "ବୃଷ", "♉"),
        RashiInfo("Mithuna (Gemini)", "ମିଥୁନ", "♊"),
        RashiInfo("Karkata (Cancer)", "କର୍କଟ", "♋"),
        RashiInfo("Singha (Leo)", "ସିଂହ", "♌"),
        RashiInfo("Kanya (Virgo)", "କନ୍ୟା", "♍"),
        RashiInfo("Tula (Libra)", "ତୁଳା", "♎"),
        RashiInfo("Bichha (Scorpio)", "ବିଛା", "♏"),
        RashiInfo("Dhanu (Sagittarius)", "ଧନୁ", "♐"),
        RashiInfo("Makara (Capricorn)", "ମକର", "♑"),
        RashiInfo("Kumbha (Aquarius)", "କୁମ୍ଭ", "♒"),
        RashiInfo("Meena (Pisces)", "ମୀନ", "♓")
    )

    private val tithis = listOf(
        TithiInfo("Pratipada", "ପ୍ରତିପଦା", 1),
        TithiInfo("Dwitiya", "ଦ୍ୱିତୀୟା", 2),
        TithiInfo("Tritiya", "ତୃତୀୟା", 3),
        TithiInfo("Chaturthi", "ଚତୁର୍ଥୀ", 4),
        TithiInfo("Panchami", "ପଞ୍ଚମୀ", 5),
        TithiInfo("Shasthi", "ଷଷ୍ଠୀ", 6),
        TithiInfo("Saptami", "ସପ୍ତମୀ", 7),
        TithiInfo("Asthami", "ଅଷ୍ଟମୀ", 8),
        TithiInfo("Navami", "ନବମୀ", 9),
        TithiInfo("Dashami", "ଦଶମୀ", 10),
        TithiInfo("Ekadashi", "ଏକାଦଶୀ", 11),
        TithiInfo("Dwadashi", "ଦ୍ୱାଦଶୀ", 12),
        TithiInfo("Trayodashi", "ତ୍ରୟୋଦଶୀ", 13),
        TithiInfo("Chaturdashi", "ଚତୁର୍ଦ୍ଦଶୀ", 14),
        TithiInfo("Purnima", "ପୂର୍ଣ୍ଣିମା", 15),
        TithiInfo("Amavasya", "ଅମାବାସ୍ୟା", 15)
    )

    val weeksEn = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    val weeksOr = listOf("ରବିବାର", "ସୋମବାର", "ମଙ୍ଗଳବାର", "ବୁଧବାର", "ଗୁରୁବାର", "ଶୁକ୍ରବାର", "ଶନିବାର")

    // Lunar events list (Amavasya & Purnima) for 2025, 2026, 2027 to calculate Tithis dynamically
    // isPurnima = true means Purnima (Full Moon), false means Amavasya (New Moon)
    private val lunarEvents = listOf(
        // Late 2024 anchors
        Pair(LocalDate.of(2024, 12, 15), false),
        Pair(LocalDate.of(2024, 12, 30), true),

        // 2025
        Pair(LocalDate.of(2025, 1, 13), true),
        Pair(LocalDate.of(2025, 1, 29), false),
        Pair(LocalDate.of(2025, 2, 12), true),
        Pair(LocalDate.of(2025, 2, 27), false),
        Pair(LocalDate.of(2025, 3, 14), true),
        Pair(LocalDate.of(2025, 3, 29), false),
        Pair(LocalDate.of(2025, 4, 12), true),
        Pair(LocalDate.of(2025, 4, 27), false),
        Pair(LocalDate.of(2025, 5, 12), true),
        Pair(LocalDate.of(2025, 5, 27), false),
        Pair(LocalDate.of(2025, 6, 11), true),
        Pair(LocalDate.of(2025, 6, 25), false),
        Pair(LocalDate.of(2025, 7, 10), true),
        Pair(LocalDate.of(2025, 7, 24), false),
        Pair(LocalDate.of(2025, 8, 9), true),
        Pair(LocalDate.of(2025, 8, 23), false),
        Pair(LocalDate.of(2025, 9, 7), true),
        Pair(LocalDate.of(2025, 9, 21), false),
        Pair(LocalDate.of(2025, 10, 7), true),
        Pair(LocalDate.of(2025, 10, 21), false),
        Pair(LocalDate.of(2025, 11, 5), true),
        Pair(LocalDate.of(2025, 11, 20), false),
        Pair(LocalDate.of(2025, 12, 4), true),
        Pair(LocalDate.of(2025, 12, 20), false),

        // 2026
        Pair(LocalDate.of(2026, 1, 3), true),
        Pair(LocalDate.of(2026, 1, 18), false),
        Pair(LocalDate.of(2026, 2, 1), true),
        Pair(LocalDate.of(2026, 2, 17), false),
        Pair(LocalDate.of(2026, 3, 3), true),
        Pair(LocalDate.of(2026, 3, 18), false),
        Pair(LocalDate.of(2026, 4, 2), true),
        Pair(LocalDate.of(2026, 4, 17), false),
        Pair(LocalDate.of(2026, 5, 2), true),
        Pair(LocalDate.of(2026, 5, 16), false),
        Pair(LocalDate.of(2026, 6, 1), true),
        Pair(LocalDate.of(2026, 6, 15), false),
        Pair(LocalDate.of(2026, 6, 30), true),
        Pair(LocalDate.of(2026, 7, 14), false),
        Pair(LocalDate.of(2026, 7, 29), true),
        Pair(LocalDate.of(2026, 8, 12), false),
        Pair(LocalDate.of(2026, 8, 28), true),
        Pair(LocalDate.of(2026, 9, 11), false),
        Pair(LocalDate.of(2026, 9, 26), true),
        Pair(LocalDate.of(2026, 10, 10), false),
        Pair(LocalDate.of(2026, 10, 25), true),
        Pair(LocalDate.of(2026, 11, 9), false),
        Pair(LocalDate.of(2026, 11, 24), true),
        Pair(LocalDate.of(2026, 12, 9), false),
        Pair(LocalDate.of(2026, 12, 24), true),

        // 2027
        Pair(LocalDate.of(2027, 1, 7), false),
        Pair(LocalDate.of(2027, 1, 22), true),
        Pair(LocalDate.of(2027, 2, 6), false),
        Pair(LocalDate.of(2027, 2, 21), true),
        Pair(LocalDate.of(2027, 3, 8), false),
        Pair(LocalDate.of(2027, 3, 22), true),
        Pair(LocalDate.of(2027, 4, 6), false),
        Pair(LocalDate.of(2027, 4, 21), true),
        Pair(LocalDate.of(2027, 5, 6), false),
        Pair(LocalDate.of(2027, 5, 20), true),
        Pair(LocalDate.of(2027, 6, 4), false),
        Pair(LocalDate.of(2027, 6, 19), true),
        Pair(LocalDate.of(2027, 7, 4), false),
        Pair(LocalDate.of(2027, 7, 18), true),
        Pair(LocalDate.of(2027, 8, 2), false),
        Pair(LocalDate.of(2027, 8, 17), true),
        Pair(LocalDate.of(2027, 9, 1), false),
        Pair(LocalDate.of(2027, 9, 15), true),
        Pair(LocalDate.of(2027, 9, 30), false),
        Pair(LocalDate.of(2027, 10, 15), true),
        Pair(LocalDate.of(2027, 10, 29), false),
        Pair(LocalDate.of(2027, 11, 14), true),
        Pair(LocalDate.of(2027, 11, 28), false),
        Pair(LocalDate.of(2027, 12, 13), true),
        Pair(LocalDate.of(2027, 12, 28), false),

        // Early 2028 anchors
        Pair(LocalDate.of(2028, 1, 12), true),
        Pair(LocalDate.of(2028, 1, 26), false)
    ).sortedBy { it.first }

    // Sankranti Dates (around mid month when Solar Odia Month changes)
    private val sankrantis2025 = listOf(
        Pair(LocalDate.of(2025, 1, 14), 9), // Magha
        Pair(LocalDate.of(2025, 2, 13), 10), // Phalguna
        Pair(LocalDate.of(2025, 3, 14), 11), // Chaitra
        Pair(LocalDate.of(2025, 4, 14), 0), // Baisakha
        Pair(LocalDate.of(2025, 5, 15), 1), // Jyestha
        Pair(LocalDate.of(2025, 6, 15), 2), // Ashadha
        Pair(LocalDate.of(2025, 7, 16), 3), // Shrabana
        Pair(LocalDate.of(2025, 8, 16), 4), // Bhadraba
        Pair(LocalDate.of(2025, 9, 16), 5), // Aswina
        Pair(LocalDate.of(2025, 10, 17), 6), // Kartika
        Pair(LocalDate.of(2025, 11, 16), 7), // Margasira
        Pair(LocalDate.of(2025, 12, 16), 8)  // Pausa
    )

    private val sankrantis2026 = listOf(
        Pair(LocalDate.of(2026, 1, 14), 9), // Magha
        Pair(LocalDate.of(2026, 2, 13), 10), // Phalguna
        Pair(LocalDate.of(2026, 3, 14), 11), // Chaitra
        Pair(LocalDate.of(2026, 4, 14), 0), // Baisakha
        Pair(LocalDate.of(2026, 5, 15), 1), // Jyestha
        Pair(LocalDate.of(2026, 6, 15), 2), // Ashadha
        Pair(LocalDate.of(2026, 7, 16), 3), // Shrabana
        Pair(LocalDate.of(2026, 8, 16), 4), // Bhadraba
        Pair(LocalDate.of(2026, 9, 16), 5), // Aswina
        Pair(LocalDate.of(2026, 10, 17), 6), // Kartika
        Pair(LocalDate.of(2026, 11, 16), 7), // Margasira
        Pair(LocalDate.of(2026, 12, 16), 8)  // Pausa
    )

    private val sankrantis2027 = listOf(
        Pair(LocalDate.of(2027, 1, 14), 9), // Magha
        Pair(LocalDate.of(2027, 2, 13), 10), // Phalguna
        Pair(LocalDate.of(2027, 3, 14), 11), // Chaitra
        Pair(LocalDate.of(2027, 4, 14), 0), // Baisakha
        Pair(LocalDate.of(2027, 5, 15), 1), // Jyestha
        Pair(LocalDate.of(2027, 6, 15), 2), // Ashadha
        Pair(LocalDate.of(2027, 7, 16), 3), // Shrabana
        Pair(LocalDate.of(2027, 8, 16), 4), // Bhadraba
        Pair(LocalDate.of(2027, 9, 16), 5), // Aswina
        Pair(LocalDate.of(2027, 10, 17), 6), // Kartika
        Pair(LocalDate.of(2027, 11, 16), 7), // Margasira
        Pair(LocalDate.of(2027, 12, 16), 8)  // Pausa
    )

    // Master Festival Database matching any date across 2025, 2026, 2027
    val festivals = listOf(
        // === 2025 ===
        Festival(LocalDate.of(2025, 1, 1), "Gregorian New Year", "ଇଂରାଜୀ ନୂତନ ବର୍ଷ", false),
        Festival(LocalDate.of(2025, 1, 14), "Makar Sankranti", "ମକର ସଂକ୍ରାନ୍ତି", true),
        Festival(LocalDate.of(2025, 1, 26), "Republic Day", "ସାଧାରଣତନ୍ତ୍ର ଦିବସ", false),
        Festival(LocalDate.of(2025, 2, 2), "Saraswati Puja / Vasant Panchami", "ସରସ୍ୱତୀ ପୂଜା / ବସନ୍ତ ପଞ୍ଚମୀ", true),
        Festival(LocalDate.of(2025, 2, 26), "Maha Shivaratri", "ମହା ଶିବରାତ୍ରି", true),
        Festival(LocalDate.of(2025, 3, 13), "Dola Purnima", "ଦୋଳ ପୂର୍ଣ୍ଣିମା", true),
        Festival(LocalDate.of(2025, 3, 14), "Holi", "ହୋଲି", true),
        Festival(LocalDate.of(2025, 4, 1), "Utkala Dibasa (Odisha Day)", "ଉତ୍କଳ ଦିବସ (ଓଡ଼ିଶା ଦିବସ)", false),
        Festival(LocalDate.of(2025, 4, 14), "Maha Vishuba Sankranti / Pana Sankranti (Odia New Year)", "ମହା ବିଷୁବ ସଂକ୍ରାନ୍ତି / ପଣା ସଂକ୍ରାନ୍ତି (ଓଡ଼ିଆ ନୂତନ ବର୍ଷ)", true),
        Festival(LocalDate.of(2025, 4, 30), "Akshaya Tritiya", "ଅକ୍ଷୟ ତୃତୀୟା", true),
        Festival(LocalDate.of(2025, 5, 27), "Sabitri Brata", "ସାବିତ୍ରୀ ବ୍ରତ", true),
        Festival(LocalDate.of(2025, 6, 1), "Sitala Sasthi", "ଶୀତଳ ଷଷ୍ଠୀ", false),
        Festival(LocalDate.of(2025, 6, 10), "Deva Snana Purnima", "ଦେବ ସ୍ନାନ ପୂର୍ଣ୍ଣିମା", true),
        Festival(LocalDate.of(2025, 6, 14), "Pahili Raja", "ପହିଲି ରଜ", true),
        Festival(LocalDate.of(2025, 6, 15), "Raja Sankranti", "ରଜ ସଂକ୍ରାନ୍ତି", true),
        Festival(LocalDate.of(2025, 6, 16), "Sesha Raja / Bhudahana", "ଶେଷ ରଜ / ଭୂଦାହନ", false),
        Festival(LocalDate.of(2025, 6, 27), "Sri Gundicha / Ratha Yatra", "ଶ୍ରୀ ଗୁଣ୍ଡିଚା / ରଥଯାତ୍ରା", true),
        Festival(LocalDate.of(2025, 7, 5), "Bahuda Yatra", "ବାହୁଡ଼ା ଯାତ୍ରା", true),
        Festival(LocalDate.of(2025, 8, 9), "Gamha Purnima (Rakhi)", "ଗହ୍ମା ପୂର୍ଣ୍ଣିମା (ରାକ୍ଷୀ)", true),
        Festival(LocalDate.of(2025, 8, 15), "Independence Day", "ସ୍ୱାଧୀନତା ଦିବସ", false),
        Festival(LocalDate.of(2025, 8, 16), "Janmashtami", "ଶ୍ରୀକୃଷ୍ଣ ଜନ୍ମାଷ୍ଟମୀ", true),
        Festival(LocalDate.of(2025, 8, 27), "Ganesh Chaturthi", "ଗଣେଶ ଚତୁର୍ଥୀ", true),
        Festival(LocalDate.of(2025, 8, 28), "Nuakhai", "ନୁଆଖାଇ (ପଶ୍ଚିମ ଓଡ଼ିଶା କୃଷି ପର୍ବ)", true),
        Festival(LocalDate.of(2025, 10, 1), "Durga Puja (Maha Ashtami)", "ଦୁର୍ଗା ପୂଜା (ମହା ଅଷ୍ଟମୀ)", true),
        Festival(LocalDate.of(2025, 10, 2), "Maha Navami / Gandhi Jayanti", "ମହା ନବମୀ / ଗାନ୍ଧୀ ଜୟନ୍ତୀ", false),
        Festival(LocalDate.of(2025, 10, 3), "Vijaya Dashami (Dussehra)", "ବିଜୟା ଦଶମୀ (ଦଶହରା)", true),
        Festival(LocalDate.of(2025, 10, 6), "Kumar Purnima / Gaja Laxmi Puja", "କୁମାର ପୂର୍ଣ୍ଣିମା / ଗଜଲକ୍ଷ୍ମୀ ପୂଜା", true),
        Festival(LocalDate.of(2025, 10, 20), "Deepavali / Kali Puja", "ଦୀପାବଳି / କାଳୀ ପୂଜା", true),
        Festival(LocalDate.of(2025, 11, 5), "Kartika Purnima / Boita Bandana", "କାର୍ତ୍ତିକ ପୂର୍ଣ୍ଣିମା / ବୋଇତ ବନ୍ଦାଣ", true),
        Festival(LocalDate.of(2025, 11, 23), "Prathamastami", "ପ୍ରଥମାଷ୍ଟମୀ", true),
        Festival(LocalDate.of(2025, 12, 11), "Samba Dashami", "ଶାମ୍ବ ଦଶମୀ", false),
        Festival(LocalDate.of(2025, 12, 25), "Christmas", "ଖ୍ରୀଷ୍ଟମାସ", false),

        // === 2026 ===
        Festival(LocalDate.of(2026, 1, 1), "Gregorian New Year", "ଇଂරාଜୀ ନୂତନ ବର୍ଷ", false),
        Festival(LocalDate.of(2026, 1, 14), "Makar Sankranti", "ମକର ସଂକ୍ରାନ୍ତି", true),
        Festival(LocalDate.of(2026, 1, 23), "Saraswati Puja / Vasant Panchami", "ସରସ୍ୱତី ପୂଜା / ବସନ୍ତ ପଞ୍ଚମୀ", true),
        Festival(LocalDate.of(2026, 1, 26), "Republic Day", "ସାଧାରଣତନ୍ତ୍ର ଦିବସ", false),
        Festival(LocalDate.of(2026, 2, 15), "Maha Shivaratri", "ମହା ଶିବରାତ୍ରି", true),
        Festival(LocalDate.of(2026, 3, 3), "Dola Purnima", "ଦୋଳ ପୂର୍ଣ୍ଣିମା", true),
        Festival(LocalDate.of(2026, 3, 4), "Holi", "ହୋଲି", true),
        Festival(LocalDate.of(2026, 4, 1), "Utkala Dibasa (Odisha Day)", "ଉତ୍କଳ ଦିବସ (ଓଡ଼ିଶା ଦିବସ)", false),
        Festival(LocalDate.of(2026, 4, 14), "Maha Vishuba Sankranti / Pana Sankranti (Odia New Year)", "ମହା ବିଷୁବ ସଂକ୍ରାନ୍ତି / ପଣା ସଂକ୍ରାନ୍ତି (ଓଡ଼ିଆ ନୂତନ ବର୍ଷ)", true),
        Festival(LocalDate.of(2026, 4, 20), "Akshaya Tritiya", "ଅକ୍ଷୟ ତୃତୀୟା", true),
        Festival(LocalDate.of(2026, 5, 15), "Sabitri Brata", "ସାବିତ୍ରୀ ବ୍ରତ", true),
        Festival(LocalDate.of(2026, 5, 21), "Sitala Sasthi", "ଶୀତଳ ଷଷ୍ଠୀ", false),
        Festival(LocalDate.of(2026, 5, 30), "Deva Snana Purnima", "ଦେବ ସ୍ନାନ ପୂର୍ଣ୍ଣିମା", true),
        Festival(LocalDate.of(2026, 6, 14), "Pahili Raja", "ପହିଲି ରଜ", true),
        Festival(LocalDate.of(2026, 6, 15), "Raja Sankranti", "ରଜ ସଂକ୍ରାନ୍ତି", true),
        Festival(LocalDate.of(2026, 6, 16), "Sesha Raja / Bhudahana", "ଶେଷ ରଜ / ଭୂଦାହନ", false),
        Festival(LocalDate.of(2026, 7, 16), "Sri Gundicha / Ratha Yatra", "ଶ୍ରୀ ଗۇଣ୍ଡିଚା / ରଥଯାତ୍ରା", true),
        Festival(LocalDate.of(2026, 7, 24), "Bahuda Yatra", "ବାହୁଡ଼ା ଯାତ୍ରା", true),
        Festival(LocalDate.of(2026, 7, 25), "Suna Besha", "ଶ୍ରୀ ଜଗନ୍ନାଥଙ୍କ ସୁନାବେଶ", true),
        Festival(LocalDate.of(2026, 8, 15), "Independence Day", "ସ୍ୱାଧୀନତା ଦିବସ", false),
        Festival(LocalDate.of(2026, 8, 24), "Janmashtami", "ଶ୍ରୀକୃଷ୍ଣ ଜନ୍ମାଷ୍ଟମୀ", true),
        Festival(LocalDate.of(2026, 8, 28), "Gamha Purnima (Rakhi)", "ଗହ୍ମା ପୂର୍ଣ୍ଣିମା (ରାକ୍ଷୀ)", true),
        Festival(LocalDate.of(2026, 9, 15), "Ganesh Chaturthi", "ଗଣେଶ ଚତୁର୍ଥୀ", true),
        Festival(LocalDate.of(2026, 9, 16), "Nuakhai", "ନୁଆଖାଇ (ପଶ୍ଚିମ ଓଡ଼ିଶା କୃଷି ପର୍ବ)", true),
        Festival(LocalDate.of(2026, 10, 2), "Gandhi Jayanti", "ଗାନ୍ଧୀ ଜୟନ୍ତୀ", false),
        Festival(LocalDate.of(2026, 10, 18), "Durga Puja (Maha Ashtami)", "ଦୁର୍ଗା ପୂଜା (ମହା ଅଷ୍ଟମୀ)", true),
        Festival(LocalDate.of(2026, 10, 19), "Durga Puja (Maha Navami)", "ଦୁର୍ଗା ପୂଜା (ମହା ନବମୀ)", true),
        Festival(LocalDate.of(2026, 10, 20), "Vijaya Dashami (Dussehra)", "ବିଜୟା ଦଶମୀ (ଦଶହରା)", true),
        Festival(LocalDate.of(2026, 10, 25), "Kumar Purnima / Gaja Laxmi Puja", "କୁମାର ପୂର୍ଣ୍ଣିମା / ଗଜଲକ୍ଷ୍ମୀ ପୂଜା", true),
        Festival(LocalDate.of(2026, 11, 8), "Deepavali / Kali Puja", "ଦୀପାବଳି / କାଳୀ ପୂଜା", true),
        Festival(LocalDate.of(2026, 11, 13), "Prathamastami", "ପ୍ରଥମାଷ୍տମୀ", true),
        Festival(LocalDate.of(2026, 11, 24), "Kartika Purnima / Boita Bandana", "କାର୍ତ୍ତିକ ପୂର୍ଣ୍ଣିମା / ବୋଇତ ବନ୍ଦାଣ", true),
        Festival(LocalDate.of(2026, 12, 19), "Samba Dashami", "ଶାମ୍ବ ଦଶମୀ", false),
        Festival(LocalDate.of(2026, 12, 25), "Christmas", "ଖ୍ରୀଷ୍ଟମାସ", false),

        // === 2027 ===
        Festival(LocalDate.of(2027, 1, 1), "Gregorian New Year", "ଇଂରାଜୀ ନୂତନ ବର୍ଷ", false),
        Festival(LocalDate.of(2027, 1, 14), "Makar Sankranti", "ମକର ସଂକ୍ରାନ୍ତି", true),
        Festival(LocalDate.of(2027, 1, 26), "Republic Day", "ସାଧାରଣତନ୍ତ୍ର ଦିବସ", false),
        Festival(LocalDate.of(2027, 2, 11), "Saraswati Puja / Vasant Panchami", "ସରସ୍ୱତୀ ପୂଜା / ବସନ୍ତ ପଞ୍ଚମୀ", true),
        Festival(LocalDate.of(2027, 3, 6), "Maha Shivaratri", "ମହା ଶିବରାତ୍ରି", true),
        Festival(LocalDate.of(2027, 3, 22), "Dola Purnima", "ଦୋଳ ପୂର୍ଣ୍ଣିମା", true),
        Festival(LocalDate.of(2027, 3, 23), "Holi", "ହୋଲି", true),
        Festival(LocalDate.of(2027, 4, 1), "Utkala Dibasa (Odisha Day)", "ଉତ୍କଳ ଦିବସ (ଓଡ଼ିଶା ଦିବସ)", false),
        Festival(LocalDate.of(2027, 4, 14), "Maha Vishuba Sankranti / Pana Sankranti (Odia New Year)", "ମହା ବିଷուବ ସଂକ୍ରାନ୍ତି / ପଣା ସଂକ୍ରାନ୍ତି (ଓଡ଼ିଆ ନୂତନ ବର୍ଷ)", true),
        Festival(LocalDate.of(2027, 5, 5), "Sabitri Brata", "ସାବିତ୍ରୀ ବ୍ରତ", true),
        Festival(LocalDate.of(2027, 5, 9), "Akshaya Tritiya", "ଅକ୍ଷୟ ତୃତୀୟା", true),
        Festival(LocalDate.of(2027, 6, 10), "Sitala Sasthi", "ଶୀତଳ ଷଷ୍ଠୀ", false),
        Festival(LocalDate.of(2027, 6, 14), "Pahili Raja", "ପହିଲି ରଜ", true),
        Festival(LocalDate.of(2027, 6, 15), "Raja Sankranti", "ରଜ ସଂକ୍ରାନ୍ତି", true),
        Festival(LocalDate.of(2027, 6, 16), "Sesha Raja / Bhudahana", "ଶେଷ ରଜ / ଭୂଦาହନ", false),
        Festival(LocalDate.of(2027, 6, 19), "Deva Snana Purnima", "ଦେବ ସ୍ନାନ ପୂର୍ଣ୍ଣିମା", true),
        Festival(LocalDate.of(2027, 7, 5), "Sri Gundicha / Ratha Yatra", "ଶ୍ରୀ ଗୁଣ୍ଡିଚା / ରଥଯାତ୍ରା", true),
        Festival(LocalDate.of(2027, 7, 13), "Bahuda Yatra", "ବାହୁଡ଼ା ଯାତ୍ରା", true),
        Festival(LocalDate.of(2027, 7, 14), "Suna Besha", "ଶ୍ରୀ ଜଗନ୍ନାଥଙ୍କ ସୁନାବେଶ", true),
        Festival(LocalDate.of(2027, 8, 15), "Independence Day", "ସ୍ୱାଧୀନତା ଦିବସ", false),
        Festival(LocalDate.of(2027, 8, 17), "Gamha Purnima (Rakhi)", "ଗହ୍ମା ପୂର୍ଣ୍ଣିମା (ରାକ୍ଷୀ)", true),
        Festival(LocalDate.of(2027, 8, 25), "Janmashtami", "ଶ୍ରୀକୃଷ୍ଣ ଜନ୍ମାଷ୍ଟମୀ", true),
        Festival(LocalDate.of(2027, 9, 4), "Ganesh Chaturthi", "ଗଣେଶ ଚତୁର୍ଥୀ", true),
        Festival(LocalDate.of(2027, 9, 5), "Nuakhai", "ନୁଆଖାଇ (ପଶ୍ଚିମ ଓଡ଼ିଶା କୃଷି ପର୍ବ)", true),
        Festival(LocalDate.of(2027, 10, 2), "Gandhi Jayanti", "ଗାନ୍ଧୀ ଜୟନ୍ତୀ", false),
        Festival(LocalDate.of(2027, 10, 7), "Durga Puja (Maha Ashtami)", "ଦୁର୍ଗା ପୂଜା (ମହା ଅଷ୍ଟମୀ)", true),
        Festival(LocalDate.of(2027, 10, 8), "Durga Puja (Maha Navami)", "ଦୁର୍ଗା ପୂଜା (ମହା ନବମୀ)", true),
        Festival(LocalDate.of(2027, 10, 9), "Vijaya Dashami (Dussehra)", "ବିଜୟା ଦଶମୀ (ଦଶହରା)", true),
        Festival(LocalDate.of(2027, 10, 15), "Kumar Purnima / Gaja Laxmi Puja", "କୁମାର ପୂର୍ଣ୍ଣିମା / ଗଜଲକ୍ଷ୍ମୀ ପୂଜା", true),
        Festival(LocalDate.of(2027, 10, 29), "Deepavali / Kali Puja", "ଦୀପାବଳି / କାଳୀ ପୂଜା", true),
        Festival(LocalDate.of(2027, 11, 14), "Kartika Purnima / Boita Bandana", "କାର୍ତ୍ତିକ ପୂର୍ଣ୍ଣିମା / ବୋଇତ ବନ୍ଦାଣ", true),
        Festival(LocalDate.of(2027, 11, 21), "Prathamastami", "ପ୍ରଥମାଷ୍ଟମୀ", true),
        Festival(LocalDate.of(2027, 12, 25), "Christmas", "ଖ୍ରୀଷ୍ଟମାସ", false)
    )

    // Dynamic fetching cache to save data loaded from the internet
    private val fetchedCache = java.util.concurrent.ConcurrentHashMap<LocalDate, OdiaDayInfo>()
    val fetchedTrigger = androidx.compose.runtime.mutableStateOf(0)

    // Dynamic year-wide festivals cache
    private val dynamicFestivals = java.util.concurrent.ConcurrentHashMap<Int, List<Festival>>()
    private val fetchedYears = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()

    fun cacheDayInfos(infos: List<OdiaDayInfo>) {
        infos.forEach { info ->
            fetchedCache[info.date] = info
        }
        // Increment trigger to notify all Compose readers to recompose
        fetchedTrigger.value++
    }

    fun hasFetchedDataForMonth(year: Int, month: Int): Boolean {
        return fetchedCache.containsKey(LocalDate.of(year, month, 1))
    }

    fun cacheYearFestivals(year: Int, list: List<Festival>) {
        dynamicFestivals[year] = list
        fetchedYears.add(year)
        // Increment trigger to notify all Compose readers to recompose
        fetchedTrigger.value++
    }

    fun hasFetchedFestivalsForYear(year: Int): Boolean {
        return year in 2025..2027 || fetchedYears.contains(year)
    }

    fun getFestivalsForYear(year: Int): List<Festival> {
        val staticList = festivals.filter { it.date.year == year }
        val dynamicList = dynamicFestivals[year] ?: emptyList()
        return (staticList + dynamicList).distinctBy { it.date to it.nameEn }
    }

    // Main API: Get all details for a given Gregorian Date
    fun getOdiaDayInfo(date: LocalDate): OdiaDayInfo {
        // Associate this query with Compose's state tracking
        @Suppress("UNUSED_VARIABLE")
        val triggerVal = fetchedTrigger.value

        // Return precisely fetched cache elements first
        fetchedCache[date]?.let { return it }

        val year = date.year
        val month = date.monthValue
        val day = date.dayOfMonth

        // 1. Calculate Solar Odia Month and Rashi
        val sankrantis = when (year) {
            2025 -> sankrantis2025
            2027 -> sankrantis2027
            else -> sankrantis2026 // Fallback 2026
        }

        // Find preceding Sankranti
        var monthIndex = 8 // Default Pausa relative to start of Year
        var rashiIndex = 8 // Default Dhanu

        val precedingSankranti = sankrantis.lastOrNull { it.first <= date }
        if (precedingSankranti != null) {
            monthIndex = precedingSankranti.second
            rashiIndex = precedingSankranti.second
        } else {
            monthIndex = 8 // Pausa
            rashiIndex = 8 // Dhanu
        }

        val odiaMonth = odiaMonths[monthIndex]
        val rashi = rashis[rashiIndex]

        // 2. Fetch Tithi and Paksha from Lunar Anchor Calculations
        val prevEventIdx = lunarEvents.indexOfLast { it.first <= date }
        val prevEvent = if (prevEventIdx != -1) lunarEvents[prevEventIdx] else lunarEvents[0]
        
        val nextEventIdx = lunarEvents.indexOfFirst { it.first > date }
        val nextEvent = if (nextEventIdx != -1) lunarEvents[nextEventIdx] else lunarEvents[lunarEvents.size - 1]

        val tithiInfo: TithiInfo
        val pakshaInfo: PakshaInfo

        if (date == prevEvent.first) {
            if (prevEvent.second) { // Purnima
                tithiInfo = tithis[14] // Purnima
                pakshaInfo = PakshaInfo("Shukla Paksha (Full Moon)", "ଶୁକ୍ଳ ପକ୍ଷ")
            } else { // Amavasya
                tithiInfo = tithis[15] // Amavasya
                pakshaInfo = PakshaInfo("Krishna Paksha (New Moon)", "କୃଷ୍ଣ ପକ୍ଷ")
            }
        } else {
            val totalDays = ChronoUnit.DAYS.between(prevEvent.first, nextEvent.first).toDouble()
            val daysSincePrev = ChronoUnit.DAYS.between(prevEvent.first, date)

            val isShukla = !prevEvent.second
            val calculatedTithi = ((daysSincePrev * 14.5) / totalDays).toInt().coerceIn(1, 14)
            tithiInfo = tithis[calculatedTithi - 1]

            if (isShukla) {
                pakshaInfo = PakshaInfo("Shukla Paksha (Waxing)", "ଶୁକ୍ଳ ପକ୍ଷ")
            } else {
                pakshaInfo = PakshaInfo("Krishna Paksha (Waning)", "କୃଷ୍ଣ ପକ୍ଷ")
            }
        }

        // 3. Find Festivals from static and dynamic databases
        val dayFestivals = getFestivalsForYear(year).filter { it.date == date }

        return OdiaDayInfo(
            date = date,
            odiaMonth = odiaMonth,
            tithi = tithiInfo,
            paksha = pakshaInfo,
            rashi = rashi,
            festivals = dayFestivals
        )
    }
}

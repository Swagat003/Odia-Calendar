package com.example

import android.os.Bundle
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.*
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Synchronously load the configured Gemini API key to avoid dynamic fetching race conditions
        val sharedPref = getSharedPreferences("OdiaCalendarPrefs", Context.MODE_PRIVATE)
        val savedKey = sharedPref.getString("gemini_api_key", "")?.trim() ?: ""
        if (savedKey.isNotEmpty()) {
            InternetCalendarService.apiKeyOverride = savedKey
        }
        
        enableEdgeToEdge()
        setContent {
            CalendarScreen()
        }
    }
}

@Composable
fun getAppColors(isDark: Boolean): AppColors {
    return if (isDark) {
        AppColors(
            bg = SacredCharcoal,
            text = SoftWhite,
            accent = SaffronLight,
            secondary = Color(0xFF3E3A30), // Curated dark gold-tone border
            cardBg = Color(0xFF2A261D),   // Curated dark highlighted card bg
            cardBorder = Color(0xFF423C2E), // Curated dark card border
            darkText = Color(0xFFDCD2C4),  // Secondary dark text on dark bg
            surface = CharcoalSurface,
            onSurfaceText = Color.White
        )
    } else {
        AppColors(
            bg = BoldBg,
            text = BoldText,
            accent = BoldAccent,
            secondary = BoldSecondary,
            cardBg = BoldCardBg,
            cardBorder = BoldCardBorder,
            darkText = BoldDarkText,
            surface = Color.White,
            onSurfaceText = BoldText
        )
    }
}

data class AppColors(
    val bg: Color,
    val text: Color,
    val accent: Color,
    val secondary: Color,
    val cardBg: Color,
    val cardBorder: Color,
    val darkText: Color,
    val surface: Color,
    val onSurfaceText: Color
)

@Composable
fun CalendarScreen() {
    val today = remember { LocalDate.of(2026, 5, 26) } // Fixed anchor for 2026 context
    var selectedDate by remember { mutableStateOf(today) }
    var currentYear by remember { mutableStateOf(2026) }
    var currentMonth by remember { mutableStateOf(5) } // May
    var isOdia by remember { mutableStateOf(true) } // Odia language toggled by default
    var showFestivalsList by remember { mutableStateOf(false) }
    var festivalSearchQuery by remember { mutableStateOf("") }
    var isCalendarVisible by remember { mutableStateOf(true) } // Option to hide calendar

    var isFetching by remember { mutableStateOf(false) }
    var fetchError by remember { mutableStateOf<String?>(null) }
    var showSearchDateDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val sharedPref = remember { context.getSharedPreferences("OdiaCalendarPrefs", Context.MODE_PRIVATE) }
    var savedApiKey by remember { mutableStateOf(sharedPref.getString("gemini_api_key", "") ?: "") }
    var showApiKeySetupDialog by remember {
        mutableStateOf(
            savedApiKey.isEmpty() && !sharedPref.getBoolean("api_key_prompt_dismissed", false)
        )
    }

    var isDbCacheLoaded by remember { mutableStateOf(false) }

    // Sync state override
    LaunchedEffect(savedApiKey) {
        InternetCalendarService.apiKeyOverride = savedApiKey.trim().ifEmpty { null }
    }

    // Load saved database cache on startup
    LaunchedEffect(Unit) {
        try {
            val dao = database.cachedDataDao()
            // Load year festivals from DB
            val savedYears = dao.getAllCachedYears()
            savedYears.forEach { entity ->
                val list = SerializationHelper.jsonStringToFestivalsList(entity.jsonData)
                OdiaCalendarData.cacheYearFestivals(entity.year, list)
            }
            // Load month days from DB
            val savedMonths = dao.getAllCachedMonths()
            savedMonths.forEach { entity ->
                val list = SerializationHelper.jsonStringToOdiaDayInfosList(entity.jsonData)
                OdiaCalendarData.cacheDayInfos(list)
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error loading cache from DB", e)
        } finally {
            isDbCacheLoaded = true
        }
    }

    val systemDark = isSystemInDarkTheme()
    var isDarkTheme by remember { mutableStateOf(systemDark) }
    val colors = getAppColors(isDarkTheme)

    // Dynamic Internet Fetching for years outside Local Data (before 2025 or after 2027)
    LaunchedEffect(currentYear, currentMonth, isDbCacheLoaded, savedApiKey) {
        if (!isDbCacheLoaded) return@LaunchedEffect
        
        val needsFetch = currentYear < 2025 || currentYear > 2027
        if (needsFetch) {
            val needsYearFestivals = !OdiaCalendarData.hasFetchedFestivalsForYear(currentYear)
            val needsMonthData = !OdiaCalendarData.hasFetchedDataForMonth(currentYear, currentMonth)
            
            if (needsYearFestivals || needsMonthData) {
                isFetching = true
                fetchError = null
                try {
                    if (needsYearFestivals) {
                        val yearFestivals = InternetCalendarService.fetchOdiaYearFestivals(currentYear)
                        if (yearFestivals != null) {
                            OdiaCalendarData.cacheYearFestivals(currentYear, yearFestivals)
                            // Save to local Room DB
                            try {
                                val jsonStr = SerializationHelper.festivalsListToJsonString(yearFestivals)
                                database.cachedDataDao().insertCachedYear(CachedYearEntity(currentYear, jsonStr))
                            } catch (ex: Exception) {
                                android.util.Log.e("MainActivity", "Error saving year festivals", ex)
                            }
                        }
                    }
                    if (needsMonthData) {
                        val data = InternetCalendarService.fetchOdiaCalendarMonth(currentYear, currentMonth)
                        if (data != null) {
                            OdiaCalendarData.cacheDayInfos(data)
                            // Save to local Room DB
                            try {
                                val jsonStr = SerializationHelper.odiaDayInfosListToJsonString(data)
                                val key = "$currentYear-${if (currentMonth < 10) "0$currentMonth" else currentMonth.toString()}"
                                database.cachedDataDao().insertCachedMonth(CachedMonthEntity(key, jsonStr))
                            } catch (ex: Exception) {
                                android.util.Log.e("MainActivity", "Error saving month data", ex)
                            }
                        } else {
                            fetchError = if (isOdia) "ଅଫଲାଇନ ଗଣନା ବ୍ୟବହାର ହେଉଛି ।" else "Using fallback approximations as dynamic update is offline."
                        }
                    }
                } catch (e: Exception) {
                    val rawMsg = e.message ?: ""
                    fetchError = if (rawMsg.contains("API key", ignoreCase = true) || rawMsg.contains("INVALID", ignoreCase = true)) {
                        if (isOdia) "ଭୁଲ୍ API ଚାବି (Invalid API Key) | ଦୟାକରି ଆପଣଙ୍କ Gemini API ଚାବି ଯାଞ୍ଚ କରନ୍ତୁ ।" else "Invalid API Key. Please verify your Gemini API key."
                    } else {
                        if (isOdia) "ତ୍ରୁଟି: $rawMsg" else "Error: $rawMsg"
                    }
                } finally {
                    isFetching = false
                }
            } else {
                fetchError = null
            }
        } else {
            fetchError = null
        }
    }

    MyApplicationTheme(darkTheme = isDarkTheme) {
        val dayInfo = remember(selectedDate, OdiaCalendarData.fetchedTrigger.value) { 
            OdiaCalendarData.getOdiaDayInfo(selectedDate) 
        }

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .testTag("calendar_screen"),
            containerColor = colors.bg
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Main Calendar Contents
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Top Festive App Header with theme toggler & language selector
                    AppHeader(
                        selectedDate = selectedDate,
                        isOdia = isOdia,
                        isDark = isDarkTheme,
                        colors = colors,
                        onLanguageToggle = { isOdia = !isOdia },
                        onThemeToggle = { isDarkTheme = !isDarkTheme }
                    )

                    // 2. Month Selector & Indicator Card (contains the Collapse Toggle)
                    MonthSelectorCard(
                        year = currentYear,
                        month = currentMonth,
                        isOdia = isOdia,
                        isCalendarVisible = isCalendarVisible,
                        colors = colors,
                        onPrevMonth = {
                            if (currentMonth == 1) {
                                if (currentYear > 1900) {
                                    currentYear--
                                    currentMonth = 12
                                }
                            } else {
                                currentMonth--
                            }
                        },
                        onNextMonth = {
                            if (currentMonth == 12) {
                                if (currentYear < 2100) {
                                    currentYear++
                                    currentMonth = 1
                                }
                            } else {
                                currentMonth++
                            }
                        },
                        onGoToToday = {
                            currentYear = today.year
                            currentMonth = today.monthValue
                            selectedDate = today
                        },
                        onToggleCalendar = {
                            isCalendarVisible = !isCalendarVisible
                        },
                        onSearchDateClick = {
                            showSearchDateDialog = true
                        }
                    )

                    // Optional Offline Alert Banner when there is a fetch error
                    if (fetchError != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                            border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "⚠️ $fetchError",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.accent,
                                modifier = Modifier.padding(12.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // 3. Collapsible Grid representation of Gregorian Days with Odia tithi subscripts
                    AnimatedVisibility(
                        visible = isCalendarVisible,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        if (isFetching) {
                            CalendarLoadingProgress(colors = colors, isOdia = isOdia)
                        } else {
                            CalendarGrid(
                                year = currentYear,
                                month = currentMonth,
                                selectedDate = selectedDate,
                                isOdia = isOdia,
                                colors = colors,
                                onDayClick = { selectedDate = it }
                            )
                        }
                    }

                    // 4. Panjika details of current selected date (including Sunrise, Sunset, Zodiac, Month)
                    SelectedDayDetailsCard(
                        dayInfo = dayInfo,
                        isOdia = isOdia,
                        colors = colors,
                        onBrowseAllFestivals = { showFestivalsList = true }
                    )

                    // Small button text only at the below of all to configure Gemini API Key
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        TextButton(
                            onClick = { showApiKeySetupDialog = true },
                            modifier = Modifier.testTag("change_api_key_btn")
                        ) {
                            Text(
                                text = if (isOdia) "Gemini API ଚାବି ପରିବର୍ତ୍ତନ କରନ୍ତୁ" else "Change Gemini API Key",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.accent.copy(alpha = 0.85f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // 5. Overlaid Animated Searchable Festivals Directory (Traditional sliding tray)
                AnimatedVisibility(
                    visible = showFestivalsList,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    FestivalsDirectorySheet(
                        isOdia = isOdia,
                        currentYear = currentYear,
                        searchQuery = festivalSearchQuery,
                        colors = colors,
                        onSearchQueryChange = { festivalSearchQuery = it },
                        onFestivalClick = { date ->
                            selectedDate = date
                            currentMonth = date.monthValue
                            currentYear = date.year
                            showFestivalsList = false
                            festivalSearchQuery = ""
                        },
                        onClose = {
                            showFestivalsList = false
                            festivalSearchQuery = ""
                        }
                    )
                }

                // 6. Floating Today's Quick-Return Button
                AnimatedVisibility(
                    visible = selectedDate != today && !showFestivalsList,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 24.dp, end = 16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .shadow(elevation = 6.dp, shape = CircleShape)
                            .clip(CircleShape)
                            .background(colors.accent)
                            .border(1.5.dp, colors.secondary, CircleShape)
                            .clickable {
                                selectedDate = today
                                currentMonth = today.monthValue
                                currentYear = today.year
                            }
                            .testTag("floating_today_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (isOdia) OdiaCalendarData.toOdiaDigits(today.dayOfMonth) else today.dayOfMonth.toString(),
                                fontSize = 16.sp,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Black,
                                color = if (isDarkTheme) Color(0xFF1D1B16) else Color.White,
                                lineHeight = 16.sp
                            )
                            Text(
                                text = getShortGregorianMonthName(today.monthValue, isOdia).uppercase(),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDarkTheme) Color(0xFF1D1B16).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.9f),
                                lineHeight = 10.sp
                            )
                        }
                    }
                }

                // 7. Search/Go to Custom Date Modal Dialog
                if (showSearchDateDialog) {
                    SearchDateDialog(
                        initialDate = selectedDate,
                        isOdia = isOdia,
                        colors = colors,
                        onDismissRequest = { showSearchDateDialog = false },
                        onDateSelected = { date ->
                            selectedDate = date
                            currentYear = date.year
                            currentMonth = date.monthValue
                        }
                    )
                }

                // 8. Gemini API Key Configuration Dialog
                if (showApiKeySetupDialog) {
                    GeminiApiKeyDialog(
                        initialKey = savedApiKey,
                        isOdia = isOdia,
                        colors = colors,
                        onDismissRequest = {
                            sharedPref.edit().putBoolean("api_key_prompt_dismissed", true).apply()
                            showApiKeySetupDialog = false
                        },
                        onKeySaved = { newKey ->
                            sharedPref.edit()
                                .putString("gemini_api_key", newKey)
                                .putBoolean("api_key_prompt_dismissed", true)
                                .apply()
                            savedApiKey = newKey
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AppHeader(
    selectedDate: LocalDate,
    isOdia: Boolean,
    isDark: Boolean,
    colors: AppColors,
    onLanguageToggle: () -> Unit,
    onThemeToggle: () -> Unit
) {
    val dayOfWeekName = getOdiaWeekday(selectedDate.dayOfWeek.value, isOdia)
    val monthName = getGregorianMonthName(selectedDate.monthValue, isOdia)
    val dayDigit = if (isOdia) OdiaCalendarData.toOdiaDigits(selectedDate.dayOfMonth) else selectedDate.dayOfMonth.toString()
    val yearText = if (isOdia) OdiaCalendarData.toOdiaDigits(selectedDate.year) else selectedDate.year.toString()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("app_header")
            .padding(top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Track text matching HTML: text-[11px] font-bold tracking-[0.15em] text-[#8C7540] uppercase
            Text(
                text = (dayOfWeekName + " • " + monthName + " " + yearText).uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.accent,
                letterSpacing = 1.8.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            // Big Serif Bold title: text-4xl font-serif font-black leading-none text-[#1D1B16]
            Text(
                text = "${monthName} ${dayDigit}",
                fontSize = 28.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Black,
                color = colors.text,
                lineHeight = 32.sp
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Theme selector round button (☀️ for dark mode to indicate "switch to light", 🌙 for light mode)
            IconButton(
                onClick = onThemeToggle,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(colors.secondary)
                    .testTag("theme_toggle_btn")
            ) {
                Text(
                    text = if (isDark) "☀️" else "🌙",
                    fontSize = 16.sp
                )
            }

            // Pill rounded switcher matching HTML beautifully
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.secondary)
                    .padding(3.dp)
                    .height(34.dp)
                    .width(96.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(if (!isOdia) colors.surface else Color.Transparent)
                        .clickable { if (isOdia) onLanguageToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "EN",
                        fontSize = 11.sp,
                        fontWeight = if (!isOdia) FontWeight.Bold else FontWeight.Medium,
                        color = if (!isOdia) colors.text else colors.darkText
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(if (isOdia) colors.surface else Color.Transparent)
                        .clickable { if (!isOdia) onLanguageToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ଓଡ଼ି",
                        fontSize = 11.sp,
                        fontWeight = if (isOdia) FontWeight.Bold else FontWeight.Medium,
                        color = if (isOdia) colors.text else colors.darkText
                    )
                }
            }
        }
    }
}

@Composable
fun MonthSelectorCard(
    year: Int,
    month: Int,
    isOdia: Boolean,
    isCalendarVisible: Boolean,
    colors: AppColors,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onGoToToday: () -> Unit,
    onToggleCalendar: () -> Unit,
    onSearchDateClick: () -> Unit
) {
    val monthName = remember(month, isOdia) { getGregorianMonthName(month, isOdia) }
    val displayYear = if (isOdia) OdiaCalendarData.toOdiaDigits(year) else year.toString()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.surface
        ),
        border = BorderStroke(1.dp, colors.secondary)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Prev month button
            IconButton(
                onClick = onPrevMonth,
                modifier = Modifier.testTag("prev_month_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowLeft,
                    contentDescription = "Previous Month",
                    tint = colors.accent,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Month text with authentic Odia lunar background
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "$monthName $displayYear",
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Black,
                    color = colors.text
                )
                Spacer(modifier = Modifier.height(2.dp))
                // Beautiful small helper displaying covering Odia Lunar Month
                Text(
                    text = getOdiaMonthOverlapText(month, isOdia),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accent
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Search Date Selector Button
                IconButton(
                    onClick = onSearchDateClick,
                    modifier = Modifier.testTag("search_date_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Date",
                        tint = colors.accent,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Collapse/Expand dynamic toggle button
                IconButton(
                    onClick = onToggleCalendar,
                    modifier = Modifier.testTag("toggle_calendar_btn")
                ) {
                    Icon(
                        imageVector = if (isCalendarVisible) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Toggle Grid Visibility",
                        tint = colors.accent,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Next month button
            IconButton(
                onClick = onNextMonth,
                modifier = Modifier.testTag("next_month_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = "Next Month",
                    tint = colors.accent,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
fun CalendarGrid(
    year: Int,
    month: Int,
    selectedDate: LocalDate,
    isOdia: Boolean,
    colors: AppColors,
    onDayClick: (LocalDate) -> Unit
) {
    val firstOfMonth = remember(year, month) { LocalDate.of(year, month, 1) }
    val daysInMonth = remember(year, month) { firstOfMonth.lengthOfMonth() }
    val dayOfWeekVal = remember(year, month) { firstOfMonth.dayOfWeek.value } // 1 (Mon) - 7 (Sun)
    
    // Calculate Grid offset so Sunday is index 0
    val offset = remember(dayOfWeekVal) { if (dayOfWeekVal == 7) 0 else dayOfWeekVal }

    val totalCells = remember(year, month, offset, daysInMonth) {
        val list = mutableListOf<LocalDate?>()
        for (i in 0 until offset) {
            list.add(null)
        }
        for (day in 1..daysInMonth) {
            list.add(LocalDate.of(year, month, day))
        }
        while (list.size % 7 != 0) {
            list.add(null)
        }
        list
    }

    // Weekday indicators in English and Odia
    val weekdays = if (isOdia) {
        listOf("ରବି", "ସୋମ", "ମଙ୍ଗଳ", "ବୁଧ", "ଗୁରୁ", "ଶୁକ୍ର", "ଶନି")
    } else {
        listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Weekday header row
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            weekdays.forEachIndexed { index, dayName ->
                Text(
                    text = dayName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (index == 0) Color(0xFFC62828) else colors.accent, // Red for Sun, Gold for others
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    letterSpacing = 1.sp
                )
            }
        }

        // Draw cells dynamically
        val rows = totalCells.chunked(7)
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEachIndexed { colIndex, cellDate ->
                    if (cellDate == null) {
                        Spacer(modifier = Modifier.weight(1f))
                    } else {
                        val isSelected = cellDate == selectedDate
                        val dayInfo = remember(cellDate, OdiaCalendarData.fetchedTrigger.value) { OdiaCalendarData.getOdiaDayInfo(cellDate) }
                        val isSunday = colIndex == 0
                        val hasFestival = dayInfo.festivals.isNotEmpty()
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(2.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    when {
                                        isSelected -> colors.cardBg // Selected background
                                        hasFestival -> Color(0xFFFFB900).copy(alpha = 0.08f) // Festival day background
                                        else -> colors.surface // Normal day background for distinct grid cells
                                    }
                                )
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = when {
                                        isSelected -> colors.accent // Bold border for selected
                                        hasFestival -> Color(0xFFFFB900).copy(alpha = 0.5f) // Warm golden for festivals
                                        else -> colors.secondary.copy(alpha = 0.35f) // Defined grid borders
                                    },
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { onDayClick(cellDate) }
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                // Day number in chosen language digits with Serif styling
                                Text(
                                    text = if (isOdia) OdiaCalendarData.toOdiaDigits(cellDate.dayOfMonth) else cellDate.dayOfMonth.toString(),
                                    fontSize = 17.sp,
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Black,
                                    color = when {
                                        isSunday -> Color(0xFFC62828)
                                        else -> colors.text
                                    }
                                )
                                
                                // Tithi Indicator under numeral
                                val tithiShort = getShortTithiName(dayInfo.tithi, isOdia)
                                Text(
                                    text = tithiShort,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) colors.accent else colors.darkText.copy(alpha = 0.65f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                // Micro dot indicating active festival on cell
                                if (hasFestival) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFE65100))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SelectedDayDetailsCard(
    dayInfo: OdiaDayInfo,
    isOdia: Boolean,
    colors: AppColors,
    onBrowseAllFestivals: () -> Unit
) {
    val tithiTitleOr = "${dayInfo.paksha.nameOr} ${dayInfo.tithi.nameOr}"
    val tithiTitleEn = "${dayInfo.paksha.nameEn.substringBefore(" (")} ${dayInfo.tithi.nameEn}"
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("details_card"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Showcase Highlight Card (Html-like styled top panel)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = colors.cardBg // BoldCardBg
            ),
            border = BorderStroke(1.dp, colors.cardBorder) // BoldCardBorder
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isOdia) "ଆଜିର ତିଥି • TODAY'S TITHI" else "TODAY'S TITHI • ତିଥି",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accent, // BoldAccent
                            letterSpacing = 1.2.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = if (isOdia) tithiTitleOr else tithiTitleEn,
                            fontSize = 28.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            color = colors.text, // BoldText
                            lineHeight = 32.sp
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isOdia) tithiTitleEn else tithiTitleOr,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.darkText.copy(alpha = 0.8f) // BoldDarkText
                        )
                    }
                    
                    // Sun / Zodiac Circle matching HTML "sunny" box
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surface.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = dayInfo.rashi.symbol,
                            fontSize = 24.sp,
                            color = colors.accent
                        )
                    }
                }
                
                // Divider match Border-t-[#DED0B0] 
                Spacer(modifier = Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.cardBorder)
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                // Bottom attributes grid (Nakshatra-style layout of Odia Month & Zodiac Rashi)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = (if (isOdia) "ଓଡ଼ିଆ ମାସ" else "ODIA MONTH").uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.text.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isOdia) dayInfo.odiaMonth.nameOr else dayInfo.odiaMonth.nameEn,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.text
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(colors.cardBorder)
                    )
                    
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = (if (isOdia) "ରାଶି" else "ZODIAC (RASHI)").uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.text.copy(alpha = 0.6f),
                            textAlign = TextAlign.End
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isOdia) dayInfo.rashi.nameOr else dayInfo.rashi.nameEn.substringBefore(" ("),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.text,
                            textAlign = TextAlign.End
                        )
                    }
                }

                // New attributes grid section for Sunrise and Sunset!
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.cardBorder)
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isOdia) "🌅 ସୂର୍ଯ୍ୟୋଦୟ (SUNRISE)" else "🌅 SUNRISE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.text.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = getSunriseTime(dayInfo.date, isOdia),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.text
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(colors.cardBorder)
                    )
                    
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = if (isOdia) "🌇 ସୂର୍ଯ୍ୟାସ୍ତ (SUNSET)" else "🌇 SUNSET",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.text.copy(alpha = 0.6f),
                            textAlign = TextAlign.End
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = getSunsetTime(dayInfo.date, isOdia),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.text,
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }

        // 2. Festivals List styled exactly like the design HTML
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header for Festivals list
            Text(
                text = if (isOdia) "ପର୍ବପର୍ବାଣୀ • FESTIVALS" else "FESTIVALS • ପର୍ବପର୍ବାଣୀ",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.accent,
                letterSpacing = 1.8.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )

            if (dayInfo.festivals.isEmpty()) {
                // Polished placeholder card for no festivals
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    border = BorderStroke(1.dp, colors.secondary)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🌸",
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (isOdia) "ଆଜି କୌଣସି ପର୍ବପର୍ବାଣୀ ନାହିଁ ।" else "No major festivals or holidays today.",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.darkText.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                dayInfo.festivals.forEach { fest ->
                    val festEmoji = when {
                        fest.isMajor -> "🪔"
                        fest.nameEn.contains("Sankranti", ignoreCase = true) -> "☀️"
                        fest.nameEn.contains("Awas", ignoreCase = true) -> "🌑"
                        fest.nameEn.contains("Purnima", ignoreCase = true) -> "🌕"
                        else -> "🚩"
                    }
                    
                    // Highlight or Normal bordered Card based on theme context
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        border = BorderStroke(1.dp, colors.secondary)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Beautiful circular icon background
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (fest.isMajor) Color(0xFFFFB900).copy(alpha = 0.12f)
                                            else colors.accent.copy(alpha = 0.08f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = festEmoji,
                                        fontSize = 18.sp
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(14.dp))
                                
                                Column {
                                    Text(
                                        text = if (isOdia) fest.nameOr else fest.nameEn,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.text
                                    )
                                    Text(
                                        text = if (isOdia) fest.nameEn else fest.nameOr,
                                        fontSize = 12.sp,
                                        color = colors.darkText.copy(alpha = 0.7f),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = "Details Indicator",
                                tint = colors.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 3. Elegant CTA action button matching style
        OutlinedButton(
            onClick = onBrowseAllFestivals,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("browse_festivals_btn"),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, colors.accent),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = colors.accent
            )
        ) {
            Icon(
                imageVector = Icons.Default.List,
                contentDescription = "List Icon",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isOdia) "ସମସ୍ତ ପର୍ବପର୍ବାଣୀ ଦେଖନ୍ତୁ" else "Browse All Holidays / Festivals",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun PanjiPropertyCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FestivalsDirectorySheet(
    isOdia: Boolean,
    currentYear: Int,
    searchQuery: String,
    colors: AppColors,
    onSearchQueryChange: (String) -> Unit,
    onFestivalClick: (LocalDate) -> Unit,
    onClose: () -> Unit
) {
    val displayedFestivals = remember(currentYear, searchQuery, isOdia, OdiaCalendarData.fetchedTrigger.value) {
        OdiaCalendarData.getFestivalsForYear(currentYear)
            .filter {
                if (searchQuery.isBlank()) true
                else {
                    it.nameEn.contains(searchQuery, ignoreCase = true) ||
                    it.nameOr.contains(searchQuery)
                }
            }
            .sortedBy { it.date }
    }

    // Sheet layout with clean, translucent background and custom card
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable { onClose() } // Close when tapping backdrop
    ) {
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .clickable(enabled = false) {}  // Consume click events inside sheet
                .testTag("festivals_directory"),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            colors = CardDefaults.cardColors(
                containerColor = colors.bg
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 12.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Drag handle bar
                Box(
                    modifier = Modifier
                        .size(40.dp, 4.dp)
                        .clip(CircleShape)
                        .background(colors.accent.copy(alpha = 0.15f))
                        .align(Alignment.CenterHorizontally)
                )

                // Header with close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isOdia) "ପର୍ବପର୍ବାଣୀ ତାଲିକା" else "Festivals Directory",
                            fontSize = 20.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Black,
                            color = colors.text
                        )
                        Text(
                            text = if (isOdia) "$currentYear ମସିହାର ସମସ୍ତ ପର୍ବ ଓ ଛୁଟିଦିନ" else "List of all festivals & holidays for $currentYear",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.darkText.copy(alpha = 0.65f)
                        )
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .background(colors.secondary, CircleShape)
                            .size(36.dp)
                            .testTag("close_sheet_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Sheet",
                            tint = colors.darkText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Search field
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_field"),
                    placeholder = {
                        Text(
                            text = if (isOdia) "ଛୁଟି କିମ୍ବା ପର୍ବ ଖୋଜନ୍ତୁ..." else "Search festival or holiday...",
                            fontSize = 14.sp,
                            color = colors.darkText.copy(alpha = 0.5f)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Icon",
                            tint = colors.accent,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colors.surface,
                        unfocusedContainerColor = colors.surface,
                        focusedIndicatorColor = colors.accent,
                        unfocusedIndicatorColor = colors.secondary,
                        focusedTextColor = colors.text,
                        unfocusedTextColor = colors.text
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // List of Holidays
                if (displayedFestivals.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isOdia) "କୌଣସି ପର୍ବପର୍ବାଣୀ ମିଳିଲା ନାହିଁ ।" else "No matching festivals found.",
                            color = colors.darkText.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(displayedFestivals) { fest ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(colors.surface)
                                    .border(
                                        width = 1.dp,
                                        color = if (fest.isMajor) Color(0xFFFFB900).copy(alpha = 0.6f) else colors.secondary.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable { onFestivalClick(fest.date) }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Date Box on Left
                                Column(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.cardBg),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = if (isOdia) OdiaCalendarData.toOdiaDigits(fest.date.dayOfMonth) else fest.date.dayOfMonth.toString(),
                                        fontSize = 16.sp,
                                        fontFamily = FontFamily.Serif,
                                        fontWeight = FontWeight.Black,
                                        color = colors.text
                                    )
                                    Text(
                                        text = getShortGregorianMonthName(fest.date.monthValue, isOdia),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.accent
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                // Festival Name & Weekday on right
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = if (isOdia) fest.nameOr else fest.nameEn,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.text
                                    )
                                    Text(
                                        text = getOdiaWeekday(fest.date.dayOfWeek.value, isOdia),
                                        fontSize = 11.sp,
                                        color = colors.darkText.copy(alpha = 0.7f),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helper methods to resolve naming queries nicely
fun OdiaDayInfo.titiNameEn(): String {
    return this.tithi.nameEn
}

fun OdiaDayInfo.titiNameOr(): String {
    return this.tithi.nameOr
}

fun getGregorianMonthName(monthVal: Int, isOdia: Boolean): String {
    val eng = listOf("", "January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    val odi = listOf("", "ଜାନୁଆରୀ", "ଫେବୃଆରୀ", "ମାର୍ଚ୍ଚ", "ଅପ୍ରେଲ", "ମଇ", "ଜୁନ୍", "ଜୁଲାଇ", "ଅଗଷ୍ଟ", "ସେପ୍ଟେମ୍ବର", "ଅକ୍ଟୋବର", "ନଭେମ୍ବର", "ଡିସେମ୍ବର")
    return if (isOdia) odi[monthVal] else eng[monthVal]
}

fun getShortGregorianMonthName(monthVal: Int, isOdia: Boolean): String {
    val eng = listOf("", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val odi = listOf("", "ଜାନୁ", "ଫେବୃ", "ମାର୍ଚ୍ଚ", "ଅପ୍ରେ", "ମଇ", "ଜୁନ୍", "ଜୁଲା", "ଅଗ", "ସେପ୍ଟେ", "ଅକ୍ଟୋ", "ନଭେ", "ଡିସେ")
    return if (isOdia) odi[monthVal] else eng[monthVal]
}

fun getOdiaWeekday(dayOfWeekVal: Int, isOdia: Boolean): String {
    // dayOfWeekVal goes 1 (Mon) to 7 (Sun)
    val eng = listOf("", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    val odi = listOf("", "ସୋମବାର", "ମଙ୍ଗଳବାର", "ବୁଧବାର", "ଗୁରୁବାର", "ଶୁକ୍ରବାର", "ଶନିବାର", "ରବିବାର")
    return if (isOdia) odi[dayOfWeekVal] else eng[dayOfWeekVal]
}

fun getShortTithiName(tithi: TithiInfo, isOdia: Boolean): String {
    return when (tithi.value) {
        15 -> if (tithi.nameEn == "Purnima") (if (isOdia) "ପୂର୍ଣ୍ଣିମା" else "Purnima") else (if (isOdia) "ଅମାବାସ୍ୟା" else "Amavasya")
        1 -> if (isOdia) "ପ୍ରତି" else "Prati"
        2 -> if (isOdia) "ଦ୍ୱିତୀୟା" else "Dwitiya"
        3 -> if (isOdia) "ତୃତୀୟା" else "Tritiya"
        4 -> if (isOdia) "ଚତୁର୍ଥୀ" else "Chaturthi"
        5 -> if (isOdia) "ପଞ୍ଚମୀ" else "Panchami"
        6 -> if (isOdia) "ଷଷ୍ଠୀ" else "Shasthi"
        7 -> if (isOdia) "ସପ୍ତମୀ" else "Saptami"
        8 -> if (isOdia) "ଅଷ୍ଟମୀ" else "Asthami"
        9 -> if (isOdia) "ନବମୀ" else "Navami"
        10 -> if (isOdia) "ଦଶମୀ" else "Dashami"
        11 -> if (isOdia) "ଏକାଦ" else "Ekadashi"
        12 -> if (isOdia) "ଦ୍ୱାଦ" else "Dwadashi"
        13 -> if (isOdia) "ତ୍ରୟୋ" else "Trayodashi"
        14 -> if (isOdia) "ଚତୁର୍ଦ୍ଦ" else "Chaturdashi"
        else -> ""
    }
}

// Helper to resolve overlapping traditional Odia months printed as headers on paper calendars
fun getOdiaMonthOverlapText(monthVal: Int, isOdia: Boolean): String {
    return when (monthVal) {
        1 -> if (isOdia) "ପୌଷ - ମାଘ" else "Pausa - Magha"
        2 -> if (isOdia) "ମାଘ - ଫାଲ୍‌ଗୁନ" else "Magha - Phalguna"
        3 -> if (isOdia) "ଫାଲ୍‌ଗୁନ - ଚୈତ୍ର" else "Phalguna - Chaitra"
        4 -> if (isOdia) "ଚୈତ୍ର - ବୈଶାଖ" else "Chaitra - Baisakha"
        5 -> if (isOdia) "ବୈଶାଖ - ଜ୍ୟେଷ୍ଠ" else "Baisakha - Jyestha"
        6 -> if (isOdia) "ଜ୍ୟେଷ୍ଠ - ଆଷାଢ଼" else "Jyestha - Ashadha"
        7 -> if (isOdia) "ଆଷାଢ଼ - ଶ୍ରାବଣ" else "Ashadha - Shrabana"
        8 -> if (isOdia) "ଶ୍ରାବଣ - ଭାଦ୍ରବ" else "Shrabana - Bhadraba"
        9 -> if (isOdia) "ଭାଦ୍ରବ - ଆଶ୍ୱିନ" else "Bhadraba - Aswina"
        10 -> if (isOdia) "ଆଶ୍ୱିନ - କାର୍ତ୍ତିକ" else "Aswina - Kartika"
        11 -> if (isOdia) "କାର୍ତ୍ତିକ - ମାର୍ଗଶିର" else "Kartika - Margasira"
        12 -> if (isOdia) "ମାର୍ଗଶିର - ପୌଷ" else "Margasira - Pausa"
        else -> ""
    }
}

// Astronomical approximations of sunrise and sunset specifically modeled for Odisha (Bhubaneswar/Puri coords)
fun getSunriseTime(date: LocalDate, isOdia: Boolean): String {
    val dayOfYear = date.dayOfYear
    // Solstice is around June 21 (day 172). 
    // In Odisha, summer sunrise is around 5:12 AM, winter is around 6:25 AM.
    val angle = (dayOfYear - 172) * 2 * Math.PI / 365
    val minutesFromMidnight = 345 + 38 * Math.cos(angle) // ranges ~307 (5:07 AM) to ~383 (6:23 AM)
    val hour = (minutesFromMidnight / 60).toInt()
    val min = (minutesFromMidnight % 60).toInt()
    val timeEn = String.format("%02d:%02d AM", hour, min)
    return if (isOdia) {
        val odiaDigits = OdiaCalendarData.toOdiaDigits(timeEn.replace(" AM", ""))
        "ପୂର୍ବାହ୍ନ $odiaDigits"
    } else {
        timeEn
    }
}

fun getSunsetTime(date: LocalDate, isOdia: Boolean): String {
    val dayOfYear = date.dayOfYear
    // In Odisha, summer sunset is around 6:30 PM, winter is around 5:12 PM.
    val angle = (dayOfYear - 172) * 2 * Math.PI / 365
    val minutesFromMidnight = 1071 + 39 * Math.cos(angle) // ranges ~1032 (5:12 PM) to ~1110 (6:30 PM)
    val hr12 = ((minutesFromMidnight / 60) - 12).toInt()
    val min = (minutesFromMidnight % 60).toInt()
    val timeEn = String.format("%02d:%02d PM", hr12, min)
    return if (isOdia) {
        val odiaDigits = OdiaCalendarData.toOdiaDigits(timeEn.replace(" PM", ""))
        "ଅପରାହ୍ନ $odiaDigits"
    } else {
        timeEn
    }
}

@Composable
fun CalendarLoadingProgress(colors: AppColors, isOdia: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .background(colors.surface, RoundedCornerShape(16.dp))
            .border(BorderStroke(1.dp, colors.secondary.copy(alpha = 0.5f)), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            CircularProgressIndicator(
                color = colors.accent,
                strokeWidth = 3.dp,
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (isOdia) "ଇଣ୍ଟରନେଟ୍‌ରୁ ଅସଲି ଓଡ଼ିଆ ପଞ୍ଜିକା ତଥ୍ୟ ଲୋଡ୍ କରାଯାଉଛି..." else "Fetching authentic Odia Panjika details via Gemini...",
                fontSize = 14.sp,
                color = colors.text,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isOdia) "ଦୟାକରି କିଛି ସମୟ ଅପେକ୍ଷା କରନ୍ତୁ" else "Please wait, constructing astrological alignments",
                fontSize = 11.sp,
                color = colors.darkText.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun CustomDropdownSelector(
    label: String,
    value: String,
    items: List<String>,
    onItemSelected: (Int) -> Unit,
    colors: AppColors,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Column {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.accent
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.secondary, RoundedCornerShape(10.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = value,
                    fontSize = 14.sp,
                    color = colors.text,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(colors.surface)
                .border(1.dp, colors.secondary, RoundedCornerShape(8.dp))
                .heightIn(max = 240.dp)
        ) {
            items.forEachIndexed { index, itemText ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = itemText,
                            color = colors.text,
                            fontSize = 14.sp
                        )
                    },
                    onClick = {
                        onItemSelected(index)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun SearchDateDialog(
    initialDate: LocalDate,
    isOdia: Boolean,
    colors: AppColors,
    onDismissRequest: () -> Unit,
    onDateSelected: (LocalDate) -> Unit
) {
    var selectedYear by remember { mutableStateOf(initialDate.year) }
    var selectedMonth by remember { mutableStateOf(initialDate.monthValue) }
    var selectedDay by remember { mutableStateOf(initialDate.dayOfMonth) }

    val years = remember { (1900..2100).toList() }
    val maxDays = remember(selectedYear, selectedMonth) {
        LocalDate.of(selectedYear, selectedMonth, 1).lengthOfMonth()
    }

    // Coerce selectedDay if it exceeds maxDays
    LaunchedEffect(maxDays) {
        if (selectedDay > maxDays) {
            selectedDay = maxDays
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.testTag("search_date_dialog"),
        confirmButton = {
            Button(
                onClick = {
                    val date = LocalDate.of(selectedYear, selectedMonth, selectedDay)
                    onDateSelected(date)
                    onDismissRequest()
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (isOdia) "ଯାଆନ୍ତୁ" else "GO TO DATE",
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(
                    text = if (isOdia) "ବାତିଲ୍" else "CANCEL",
                    color = colors.text.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )
            }
        },
        title = {
            Text(
                text = if (isOdia) "ତାରିଖ ଖୋଜନ୍ତୁ" else "Select Custom Date",
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Black,
                color = colors.text,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isOdia) "ଆପଣ କ୍ୟାଲେଣ୍ଡରର ଯେକୌଣସି ତାରିଖକୁ ଯାଇପାରିବେ ।" else "Navigate easily to any year, month, or day.",
                    fontSize = 12.sp,
                    color = colors.darkText,
                    textAlign = TextAlign.Center
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Day Selector (index 0 is day 1)
                    val daysList = (1..maxDays).map { if (isOdia) OdiaCalendarData.toOdiaDigits(it) else it.toString() }
                    CustomDropdownSelector(
                        label = if (isOdia) "ତାରିଖ" else "DAY",
                        value = if (isOdia) OdiaCalendarData.toOdiaDigits(selectedDay) else selectedDay.toString(),
                        items = daysList,
                        onItemSelected = { selectedDay = it + 1 },
                        colors = colors,
                        modifier = Modifier.weight(1f)
                    )

                    // Month Selector (index 0 is month 1)
                    val monthsList = (1..12).map { getGregorianMonthName(it, isOdia) }
                    CustomDropdownSelector(
                        label = if (isOdia) "ମାସ" else "MONTH",
                        value = getShortGregorianMonthName(selectedMonth, isOdia),
                        items = monthsList,
                        onItemSelected = { selectedMonth = it + 1 },
                        colors = colors,
                        modifier = Modifier.weight(1.3f)
                    )

                    // Year Selector
                    val yearsList = years.map { if (isOdia) OdiaCalendarData.toOdiaDigits(it) else it.toString() }
                    CustomDropdownSelector(
                        label = if (isOdia) "ବର୍ଷ" else "YEAR",
                        value = if (isOdia) OdiaCalendarData.toOdiaDigits(selectedYear) else selectedYear.toString(),
                        items = yearsList,
                        onItemSelected = { selectedYear = years[it] },
                        colors = colors,
                        modifier = Modifier.weight(1.1f)
                    )
                }
            }
        },
        containerColor = colors.bg
    )
}

@Composable
fun GeminiApiKeyDialog(
    initialKey: String,
    isOdia: Boolean,
    colors: AppColors,
    onDismissRequest: () -> Unit,
    onKeySaved: (String) -> Unit
) {
    var apiKeyText by remember { mutableStateOf(initialKey) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.testTag("gemini_api_key_dialog"),
        confirmButton = {
            Button(
                onClick = {
                    onKeySaved(apiKeyText.trim())
                    onDismissRequest()
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (isOdia) "ସେଭ୍ କରନ୍ତୁ" else "SAVE KEY",
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text(
                    text = if (isOdia) "ବାତିଲ୍" else "CANCEL",
                    color = colors.text.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = if (isOdia) "Gemini API ଚାବି ବ୍ୟବସ୍ଥା" else "Gemini API Key Setup",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Black,
                    color = colors.text
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = if (isOdia)
                        "ଓଡ଼ିଆ କ୍ୟାଲେଣ୍ଡର ୨୦୨୫-୨୦୨୭ ସୀମା ବାହାରେ ଥିବା ବର୍ଷଗୁଡ଼ିକ (୧୯୦୦ ରୁ ୨୧୦୦) ପାଇଁ ସମ୍ପୂର୍ଣ୍ଣ ଆଷ୍ଟ୍ରୋଲୋଜିକାଲ୍ ପଞ୍ଜିକା, ତିଥି ଏବଂ ପର୍ବପର୍ବାଣୀ ତଥ୍ୟ ଗୁଗଲ୍ Gemini AI ମାଧ୍ୟମରେ ହିସାବ କରେ । ଏଥିପାଇଁ ଏକ Gemini API ଚାବି ଆବଶ୍ୟକ ।"
                    else
                        "To generate traditional Panjika, tithi, and festival details for years outside 2025-2027 (from 1900 to 2100), the calendar queries Google's Gemini API dynamically. This requires a personal Gemini API key.",
                    fontSize = 12.sp,
                    color = colors.darkText,
                    lineHeight = 16.sp
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.secondary.copy(alpha = 0.3f))
                )

                Text(
                    text = if (isOdia) "ଚାବି ପାଇବା ପାଇଁ ନିମ୍ନ ପଦକ୍ଷେପ ଅନୁସରଣ କରନ୍ତୁ:" else "Instructions to get your free API key:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accent
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val step1 = if (isOdia) "୧. ଗୁଗଲ୍ AI ଷ୍ଟୁଡିଓ (Google AI Studio) କୁ ଯାଆନ୍ତୁ ।" else "1. Go to Google AI Studio to get a free key."
                    val step2 = if (isOdia) "୨. 'Create API Key' ଉପରେ କ୍ଲିକ୍ କରି ନିଜ ନୂଆ ଚାବି କପି କରନ୍ତୁ ।" else "2. Tap 'Create API Key' and copy the generated string."
                    val step3 = if (isOdia) "୩. କପି ହୋଇଥିବା ଚାବିକୁ ନିମ୍ନ ଲିଖିତ ବାକ୍ସରେ ପେଷ୍ଟ କରି ସେଭ୍ କରନ୍ତୁ ।" else "3. Paste your key in the field below and hit SAVE."

                    Text(text = step1, fontSize = 12.sp, color = colors.text)
                    Text(text = step2, fontSize = 12.sp, color = colors.text)
                    Text(text = step3, fontSize = 12.sp, color = colors.text)
                }

                Button(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/"))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // ignore
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.secondary.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Open AI Studio",
                        modifier = Modifier.size(16.dp),
                        tint = colors.accent
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isOdia) "AI ଷ୍ଟୁଡିଓ ୱେବସାଇଟ୍ ଖୋଲନ୍ତୁ" else "Open Google AI Studio",
                        color = colors.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.secondary.copy(alpha = 0.3f))
                )

                TextField(
                    value = apiKeyText,
                    onValueChange = { apiKeyText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, colors.secondary, RoundedCornerShape(10.dp))
                        .testTag("gemini_api_key_field"),
                    placeholder = {
                        Text(
                            text = if (isOdia) "ଏଠାରେ ଆପଣଙ୍କ ଚାବି ଲେଖନ୍ତୁ..." else "Paste your key here (e.g. AIzaSy...)",
                            fontSize = 12.sp,
                            color = colors.darkText.copy(alpha = 0.5f)
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colors.surface,
                        unfocusedContainerColor = colors.surface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = colors.text,
                        unfocusedTextColor = colors.text
                    ),
                    singleLine = true
                )
            }
        },
        containerColor = colors.bg
    )
}

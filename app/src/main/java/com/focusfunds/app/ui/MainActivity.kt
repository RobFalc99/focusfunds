package com.focusfunds.app.ui

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusfunds.app.data.BlockedApp
import com.focusfunds.app.data.FocusFundsRepository
import com.focusfunds.app.data.WalletState
import com.focusfunds.app.service.AppBlockAccessibilityService
import com.focusfunds.app.service.FocusForegroundService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FocusFundsApp()
        }
    }
}

data class AppInfo(val packageName: String, val appName: String)

fun getInstalledApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN, null).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
    val resolveInfos = pm.queryIntentActivities(intent, 0)
    return resolveInfos.map { resolveInfo ->
        AppInfo(
            packageName = resolveInfo.activityInfo.packageName,
            appName = resolveInfo.loadLabel(pm).toString()
        )
    }.distinctBy { it.packageName }
        .filter { it.packageName != context.packageName }
        .sortedBy { it.appName }
}

fun isAccessibilityServiceEnabled(context: Context, service: Class<out AccessibilityService>): Boolean {
    val expectedComponentName = ComponentName(context, service)
    val enabledServicesSetting = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    val colonSplitter = TextUtils.SimpleStringSplitter(':')
    colonSplitter.setString(enabledServicesSetting)
    while (colonSplitter.hasNext()) {
        val componentNameString = colonSplitter.next()
        val enabledService = ComponentName.unflattenFromString(componentNameString)
        if (enabledService != null && enabledService == expectedComponentName) {
            return true
        }
    }
    return false
}

@Composable
fun FocusFundsApp() {
    val context = LocalContext.current
    val repository = remember { FocusFundsRepository(context.applicationContext) }
    val walletState by repository.walletStateFlow.collectAsState(initial = null)
    
    var selectedTab by remember { mutableStateOf(0) }
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasAccessibilityPermission by remember {
        mutableStateOf(isAccessibilityServiceEnabled(context, AppBlockAccessibilityService::class.java))
    }

    // Refresh permissions check on resume
    LaunchedEffect(Unit) {
        while (true) {
            hasOverlayPermission = Settings.canDrawOverlays(context)
            hasAccessibilityPermission = isAccessibilityServiceEnabled(context, AppBlockAccessibilityService::class.java)
            delay(2000)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0F0F0F),
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
                    label = { Text("Wallet", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFD4AF37),
                        unselectedIconColor = Color.Gray,
                        selectedTextColor = Color(0xFFD4AF37),
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color(0xFF1E1E1E)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Lock, contentDescription = "App Blocker") },
                    label = { Text("Blocco App", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFD4AF37),
                        unselectedIconColor = Color.Gray,
                        selectedTextColor = Color(0xFFD4AF37),
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color(0xFF1E1E1E)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.List, contentDescription = "Statement") },
                    label = { Text("Movimenti", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFD4AF37),
                        unselectedIconColor = Color.Gray,
                        selectedTextColor = Color(0xFFD4AF37),
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color(0xFF1E1E1E)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Refresh, contentDescription = "Reports") },
                    label = { Text("Report", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFD4AF37),
                        unselectedIconColor = Color.Gray,
                        selectedTextColor = Color(0xFFD4AF37),
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color(0xFF1E1E1E)
                    )
                )
            }
        },
        containerColor = Color(0xFF000000)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (!hasOverlayPermission || !hasAccessibilityPermission) {
                PermissionOverlay(
                    hasOverlay = hasOverlayPermission,
                    hasAccess = hasAccessibilityPermission,
                    onRequestOverlay = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    },
                    onRequestAccess = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                    }
                )
            } else {
                when (selectedTab) {
                    0 -> DashboardTab(walletState, repository)
                    1 -> AppBlockerTab(repository)
                    2 -> StatementTab(repository)
                    3 -> ReportTab(repository)
                }
            }
        }
    }
}

@Composable
fun PermissionOverlay(
    hasOverlay: Boolean,
    hasAccess: Boolean,
    onRequestOverlay: () -> Unit,
    onRequestAccess: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "CONFIGURAZIONE RICHIESTA",
            color = Color(0xFFD4AF37),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "FocusFunds richiede permessi speciali per intercettare l'apertura delle app e mostrare l'overlay di sblocco POS.",
            color = Color.LightGray,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(32.dp))

        if (!hasOverlay) {
            Button(
                onClick = onRequestOverlay,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E1E)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFD4AF37), RoundedCornerShape(8.dp))
            ) {
                Text("Autorizza Sovrapposizione (Overlay)", color = Color(0xFFD4AF37))
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (!hasAccess) {
            Button(
                onClick = onRequestAccess,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E1E)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFD4AF37), RoundedCornerShape(8.dp))
            ) {
                Text("Abilita Servizio Accessibilità", color = Color(0xFFD4AF37))
            }
        }
    }
}

@Composable
fun DashboardTab(
    walletState: WalletState?,
    repository: FocusFundsRepository
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val inFocus = walletState?.inFocusMode == true
    val balance = walletState?.balance ?: 5.0
    val startTimestamp = walletState?.focusStartTimestamp ?: 0L

    var liveDurationMs by remember { mutableStateOf(0L) }

    LaunchedEffect(inFocus, startTimestamp) {
        if (inFocus && startTimestamp > 0L) {
            while (true) {
                liveDurationMs = System.currentTimeMillis() - startTimestamp
                delay(1000)
            }
        } else {
            liveDurationMs = 0L
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Header
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "PORTAFOGLIO PERSONALE",
                color = Color(0xFFD4AF37),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (inFocus) "Focus Mode Attiva" else "Pronto per la Concentrazione",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Light
            )
        }

        // Virtual Credit Card (3D Luxury Design)
        Box(
            modifier = Modifier
                .width(300.dp)
                .height(180.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF222222), Color(0xFF070707))
                    )
                )
                .border(
                    width = 2.dp,
                    brush = Brush.sweepGradient(
                        colors = listOf(Color(0xFFD4AF37), Color(0xFFC0C0C0), Color(0xFF996515), Color(0xFFD4AF37))
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "FocusFunds",
                        color = Color(0xFFD4AF37),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "F",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif
                    )
                }

                Column {
                    Text(
                        text = "SALDO ATTUALE",
                        color = Color.Gray,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "${String.format("%.2f", balance)} FF",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "L'OTTIMIZZATORE",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "BLACK CARD",
                        color = Color(0xFFD4AF37),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Live Timer / Stats
        if (inFocus) {
            val totalSecs = liveDurationMs / 1000
            val hours = totalSecs / 3600
            val minutes = (totalSecs % 3600) / 60
            val seconds = totalSecs % 60
            val liveFunds = (liveDurationMs / 60000.0) * 0.1

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = String.format("%02d:%02d:%02d", hours, minutes, seconds),
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "+${String.format("%.4f", liveFunds)} FF accumulati",
                    color = Color(0xFFD4AF37),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Tasso di accumulo",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Text(
                    text = "10 Minuti = 1.0 FF (0.1 FF/min)",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Action Button
        if (!inFocus) {
            Button(
                onClick = {
                    val intent = Intent(context, FocusForegroundService::class.java).apply {
                        action = FocusForegroundService.ACTION_START
                    }
                    context.startService(intent)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = "INIZIA FOCUS",
                    color = Color.Black,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        } else {
            // 5 Second Safety Hold Button
            var holdProgress by remember { mutableStateOf(0f) }
            var isHolding by remember { mutableStateOf(false) }

            LaunchedEffect(isHolding) {
                if (isHolding) {
                    val start = System.currentTimeMillis()
                    while (isHolding) {
                        val elapsed = System.currentTimeMillis() - start
                        holdProgress = (elapsed / 5000f).coerceIn(0f, 1f)
                        if (holdProgress >= 1f) {
                            val intent = Intent(context, FocusForegroundService::class.java).apply {
                                action = FocusForegroundService.ACTION_STOP
                            }
                            context.startService(intent)
                            isHolding = false
                        }
                        delay(20)
                    }
                } else {
                    holdProgress = 0f
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1A0000))
                    .border(1.dp, Color(0xFF8B0000), RoundedCornerShape(12.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isHolding = true
                                tryAwaitRelease()
                                isHolding = false
                            }
                        )
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                // Progress background
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(holdProgress)
                        .background(Color(0xFF8B0000).copy(alpha = 0.5f))
                )
                
                Text(
                    text = if (isHolding) "TIENI PREMUTO... (${(5 - (holdProgress * 5)).toInt() + 1}s)" else "TIENI PREMUTO 5 SECONDI PER USCIRE",
                    color = Color(0xFFFF5252),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    letterSpacing = 1.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun AppBlockerTab(repository: FocusFundsRepository) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val blockedApps by repository.blockedAppsFlow.collectAsState(initial = emptyList())
    var installedApps by remember { mutableStateOf(emptyList<AppInfo>()) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        installedApps = getInstalledApps(context)
    }

    val filteredApps = installedApps.filter {
        it.appName.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "APPLICAZIONI BLOCCATE",
            color = Color(0xFFD4AF37),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Cerca app da bloccare...", color = Color.Gray) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFFD4AF37),
                unfocusedBorderColor = Color(0xFF222222)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            singleLine = true
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredApps) { app ->
                val isBlocked = blockedApps.any { it.packageName == app.packageName }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F0F0F))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = app.appName,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = app.packageName,
                            color = Color.Gray,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Switch(
                        checked = isBlocked,
                        onCheckedChange = { checked ->
                            coroutineScope.launch {
                                if (checked) {
                                    repository.addBlockedApp(BlockedApp(app.packageName, app.appName))
                                } else {
                                    repository.removeBlockedApp(BlockedApp(app.packageName, app.appName))
                                }
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Color(0xFFD4AF37),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color(0xFF1E1E1E)
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun StatementTab(repository: FocusFundsRepository) {
    val transactions by repository.transactionsFlow.collectAsState(initial = emptyList())
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.ITALIAN) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "ESTRATTO CONTO E MOVIMENTI",
            color = Color(0xFFD4AF37),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (transactions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Nessuna transazione registrata",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(transactions) { tx ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F0F0F))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = tx.description,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = dateFormat.format(Date(tx.timestamp)),
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                        }
                        
                        val isDeposit = tx.type == "DEPOSIT"
                        val isEmergency = tx.type == "EMERGENCY"
                        val color = when {
                            isDeposit -> Color(0xFFD4AF37)
                            isEmergency -> Color(0xFFCF6679)
                            else -> Color.White
                        }
                        val prefix = when {
                            isDeposit -> "+"
                            else -> "-"
                        }
                        val suffix = if (isEmergency) " (EMERGENCY)" else ""

                        Text(
                            text = "$prefix${String.format("%.2f", tx.amount)} FF$suffix",
                            color = color,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReportTab(repository: FocusFundsRepository) {
    val transactions by repository.transactionsFlow.collectAsState(initial = emptyList())

    val weeklyStats = remember(transactions) {
        val now = System.currentTimeMillis()
        val oneWeekMs = 7 * 24 * 60 * 60 * 1000L
        val weeklyTxs = transactions.filter { now - it.timestamp <= oneWeekMs }

        val depositSum = weeklyTxs.filter { it.type == "DEPOSIT" && it.description != "Welcome Bonus" }.sumOf { it.amount }
        // 0.1 FF = 10 minutes focus. Minutes = FF * 10.0
        val focusMinutes = depositSum * 10.0

        val withdrawalSum = weeklyTxs.filter { it.type == "WITHDRAWAL" || it.type == "EMERGENCY" }.sumOf { it.amount }
        // 1.0 FF = 1 minute unlock. Minutes = FF
        val unlockMinutes = withdrawalSum

        val totalTime = focusMinutes + unlockMinutes
        val savingRate = if (totalTime > 0) {
            (focusMinutes / totalTime) * 100
        } else {
            100.0 // Default to 100 if no usage
        }

        Triple(focusMinutes, unlockMinutes, savingRate)
    }

    val (focusMins, unlockMins, savingRate) = weeklyStats

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "ESTRATTO SETTIMANALE",
                color = Color(0xFFD4AF37),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Ultimi 7 Giorni",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Light
            )
        }

        // Saving Rate Circular Indicator
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(CircleShape)
                .background(Color(0xFF0F0F0F))
                .border(2.dp, Color(0xFF1E1E1E), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = (savingRate / 100f).toFloat(),
                color = Color(0xFFD4AF37),
                strokeWidth = 8.dp,
                modifier = Modifier.fillMaxSize().padding(8.dp),
                trackColor = Color(0xFF222222)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${String.format("%.1f", savingRate)}%",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "TASSO DI RISPARMIO",
                    color = Color.Gray,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        // Stats detail row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F0F0F))
                .border(1.dp, Color(0xFF1E1E1E), RoundedCornerShape(12.dp))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "TEMPO FOCUS",
                    color = Color(0xFFD4AF37),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                val hrs = focusMins.toInt() / 60
                val mins = focusMins.toInt() % 60
                Text(
                    text = if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Divider(
                modifier = Modifier
                    .height(40.dp)
                    .width(1.dp),
                color = Color(0xFF1E1E1E)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "TEMPO SBLOCCHI",
                    color = Color.Gray,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                val hrs = unlockMins.toInt() / 60
                val mins = unlockMins.toInt() % 60
                Text(
                    text = if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Tips
        Text(
            text = if (savingRate >= 80.0) {
                "Ottimo lavoro! Stai mantenendo la distrazione come bene di lusso."
            } else if (savingRate >= 50.0) {
                "Buono, ma puoi fare di meglio. Incrementa le tue sessioni di Focus."
            } else {
                "Attenzione: stai spendendo troppo tempo sui social media. Accumula più FF!"
            },
            color = Color.LightGray,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

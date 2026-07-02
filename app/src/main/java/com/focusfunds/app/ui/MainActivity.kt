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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusfunds.app.data.BlockedApp
import com.focusfunds.app.data.FocusFundsRepository
import com.focusfunds.app.data.Transaction
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

// App data structures and package retrieval
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

// Subtle, slow-moving luxury background simulating brushed metal lights
@Composable
fun LuxuryDynamicBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "ambient_light")
    
    val posX by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "posX"
    )
    val posY by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(16000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "posY"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(Color.Black)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF0F0F12), // Deep Anthracite
                    Color(0xFF000000)
                ),
                center = Offset(size.width * posX, size.height * posY),
                radius = size.width * 1.6f
            )
        )
    }
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

    val inFocus = walletState?.inFocusMode == true

    LaunchedEffect(Unit) {
        while (true) {
            hasOverlayPermission = Settings.canDrawOverlays(context)
            hasAccessibilityPermission = isAccessibilityServiceEnabled(context, AppBlockAccessibilityService::class.java)
            delay(2000)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Shared Dynamic Luxury Background
        LuxuryDynamicBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
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
                // If in Focus Mode, hide the Tab Selector (OLED minimalist experience)
                if (!inFocus) {
                    Spacer(modifier = Modifier.height(16.dp))
                    PaddingWrapper {
                        LuxuryTabSelector(selectedTab = selectedTab, onTabSelected = { selectedTab = it })
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (if (inFocus) 0 else selectedTab) {
                        0 -> DashboardTab(walletState, repository)
                        1 -> AppBlockerTab(repository)
                        2 -> StatementTab(repository)
                        3 -> ReportTab(repository)
                    }
                }
            }
        }
    }
}

@Composable
fun LuxuryTabSelector(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    val tabs = listOf("Wallet", "App", "Movimenti", "Report")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0F0F0F).copy(alpha = 0.8f))
            .border(1.dp, Color(0xFF1E1E1E), RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        tabs.forEachIndexed { index, title ->
            val isSelected = selectedTab == index
            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected) Color(0xFF1E1E1E) else Color.Transparent,
                animationSpec = tween(250), label = "tabBg"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) Color(0xFFD4AF37) else Color.Gray,
                animationSpec = tween(250), label = "tabText"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(backgroundColor)
                    .clickable { onTabSelected(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    color = textColor,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun PaddingWrapper(content: @Composable () -> Unit) {
    Box(modifier = Modifier.padding(horizontal = 24.dp)) {
        content()
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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F0F0F).copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .border(1.dp, Color(0xFFD4AF37), RoundedCornerShape(12.dp))
            ) {
                Text("Autorizza Sovrapposizione (Overlay)", color = Color(0xFFD4AF37), fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (!hasAccess) {
            Button(
                onClick = onRequestAccess,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F0F0F).copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .border(1.dp, Color(0xFFD4AF37), RoundedCornerShape(12.dp))
            ) {
                Text("Abilita Servizio Accessibilità", color = Color(0xFFD4AF37), fontSize = 13.sp)
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

    // Shimmer reflection effect
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer_glare_dashboard")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -350f,
        targetValue = 950f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )

    // Calculate current live balance (0.01 FF every 6 seconds)
    val liveBalance = if (inFocus) {
        val elapsedMs = System.currentTimeMillis() - startTimestamp
        // 0.01 FF every 6 seconds (6000 ms)
        balance + (elapsedMs / 60000.0) * 0.1
    } else {
        balance
    }

    // Card 3D floating animation when focusing
    val cardScale by animateFloatAsState(
        targetValue = if (inFocus) 1.05f else 1f,
        animationSpec = tween(800, easing = EaseInOutCirc),
        label = "cardScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Minimal Title Header
        if (!inFocus) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "WALLET PERSONALE",
                    color = Color(0xFFD4AF37),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Nessuna sessione di concentrazione attiva",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Light
                )
            }
        } else {
            // Emptiness/Minimalism when focusing
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Protagonist Luxury Credit Card
        val reflectionBrush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                Color.White.copy(alpha = 0.04f),
                Color.White.copy(alpha = 0.14f),
                Color.White.copy(alpha = 0.04f),
                Color.Transparent
            ),
            start = Offset(shimmerOffset, 0f),
            end = Offset(shimmerOffset + 150f, 320f)
        )

        Box(
            modifier = Modifier
                .width(310.dp * cardScale)
                .height(185.dp * cardScale)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF1E1E1E), Color(0xFF030303))
                    )
                )
                .border(
                    width = 1.5.dp,
                    brush = Brush.sweepGradient(
                        colors = listOf(Color(0xFFD4AF37), Color(0xFF332205), Color(0xFFF3E5AB), Color(0xFFD4AF37))
                    ),
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            // Shiny reflection sweep
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(reflectionBrush)
            )

            // Card face details
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(22.dp),
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
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "F",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif
                    )
                }

                // Balance display engraved directly on card
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = String.format(Locale.US, "%.2f FF", liveBalance),
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = 1.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "L'OTTIMIZZATORE",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraLight,
                        letterSpacing = 1.5.sp
                    )
                    
                    // Smart Chip canvas
                    Canvas(modifier = Modifier.size(30.dp, 20.dp)) {
                        drawRoundRect(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFFE5C060), Color(0xFFC59F3F))
                            ),
                            size = size,
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                        drawLine(Color(0xFF3C2F0F), Offset(10.dp.toPx(), 0f), Offset(10.dp.toPx(), 20.dp.toPx()), strokeWidth = 1f)
                        drawLine(Color(0xFF3C2F0F), Offset(20.dp.toPx(), 0f), Offset(20.dp.toPx(), 20.dp.toPx()), strokeWidth = 1f)
                        drawLine(Color(0xFF3C2F0F), Offset(0f, 10.dp.toPx()), Offset(30.dp.toPx(), 10.dp.toPx()), strokeWidth = 1f)
                        drawRoundRect(
                            color = Color(0xFF3C2F0F),
                            topLeft = Offset(10.dp.toPx(), 5.dp.toPx()),
                            size = Size(10.dp.toPx(), 10.dp.toPx()),
                            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                            style = Stroke(1f)
                        )
                    }
                }
            }
        }

        // Minimalist info when not focusing
        if (!inFocus) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "TASSO DI ACCUMULO ATTIVO",
                    color = Color.Gray,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "0.01 FF guadagnati ogni 6 secondi",
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        } else {
            // Emptiness in focus screen to keep user concentrated
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Start Button / Circular Exit Progress
        if (!inFocus) {
            Button(
                onClick = {
                    val intent = Intent(context, FocusForegroundService::class.java).apply {
                        action = FocusForegroundService.ACTION_START
                    }
                    context.startService(intent)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37)),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .border(1.dp, Color(0xFFF3E5AB), RoundedCornerShape(28.dp))
            ) {
                Text(
                    text = "INIZIA FOCUS",
                    color = Color.Black,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
            }
        } else {
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

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(96.dp)
                ) {
                    CircularProgressIndicator(
                        progress = holdProgress,
                        color = Color(0xFFFF5252),
                        strokeWidth = 3.dp,
                        modifier = Modifier.fillMaxSize(),
                        trackColor = Color(0xFF1E1E1E)
                    )
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(if (isHolding) Color(0xFF2E0909) else Color(0xFF0F0F0F))
                            .border(1.dp, if (isHolding) Color(0xFFFF5252) else Color(0xFF1E1E1E), CircleShape)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        isHolding = true
                                        tryAwaitRelease()
                                        isHolding = false
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "STOP",
                            color = Color(0xFFFF5252),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (isHolding) "Mantieni premuto..." else "TIENI PREMUTO PER COMPLETARE FOCUS",
                    color = Color.Gray,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
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
            .padding(horizontal = 24.dp)
    ) {
        Text(
            text = "APPLICAZIONI SOTTO CONTROLLO",
            color = Color(0xFFD4AF37),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Cerca app da bloccare...", color = Color.Gray, fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Cerca", tint = Color.Gray) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFFD4AF37),
                unfocusedBorderColor = Color(0xFF1E1E1E),
                focusedLabelColor = Color(0xFFD4AF37)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F0F0F).copy(alpha = 0.5f)),
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
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0F0F0F).copy(alpha = 0.7f))
                        .border(1.dp, Color(0xFF1E1E1E), RoundedCornerShape(14.dp))
                        .padding(horizontal = 18.dp, vertical = 14.dp),
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
                            uncheckedTrackColor = Color(0xFF1A1A1A),
                            uncheckedBorderColor = Color(0xFF222222)
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
    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.ITALIAN) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Text(
            text = "CRONOLOGIA TRANSAZIONI",
            color = Color(0xFFD4AF37),
            fontSize = 10.sp,
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
                    text = "Nessuna transazione effettuata",
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
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF0F0F0F).copy(alpha = 0.7f))
                            .border(1.dp, Color(0xFF1E1E1E), RoundedCornerShape(14.dp))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            val isDeposit = tx.type == "DEPOSIT"
                            val isEmergency = tx.type == "EMERGENCY"
                            
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isDeposit -> Color(0xFF221A05)
                                            isEmergency -> Color(0xFF33050C)
                                            else -> Color(0xFF111111)
                                        }
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = when {
                                            isDeposit -> Color(0xFFD4AF37)
                                            isEmergency -> Color(0xFFFF5252)
                                            else -> Color(0xFF222222)
                                        },
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isDeposit) "F" else "POS",
                                    color = if (isDeposit) Color(0xFFD4AF37) else if (isEmergency) Color(0xFFFF5252) else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Column {
                                Text(
                                    text = tx.description,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = dateFormat.format(Date(tx.timestamp)),
                                    color = Color.Gray,
                                    fontSize = 10.sp
                                )
                            }
                        }
                        
                        val isDeposit = tx.type == "DEPOSIT"
                        val isEmergency = tx.type == "EMERGENCY"
                        val color = when {
                            isDeposit -> Color(0xFFD4AF37)
                            isEmergency -> Color(0xFFFF5252)
                            else -> Color.White
                        }
                        val prefix = if (isDeposit) "+" else "-"
                        val label = if (isEmergency) " (EMERGENCY)" else ""

                        Text(
                            text = "$prefix${String.format(Locale.US, "%.2f", tx.amount)} FF$label",
                            color = color,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
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
        val focusMinutes = depositSum * 10.0

        val withdrawalSum = weeklyTxs.filter { it.type == "WITHDRAWAL" || it.type == "EMERGENCY" }.sumOf { it.amount }
        val unlockMinutes = withdrawalSum

        val totalTime = focusMinutes + unlockMinutes
        val savingRate = if (totalTime > 0) {
            (focusMinutes / totalTime) * 100
        } else {
            100.0
        }

        Triple(focusMinutes, unlockMinutes, savingRate)
    }

    val (focusMins, unlockMins, savingRate) = weeklyStats

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "ATTIVITÀ E RISPARMIO SETTIMANALE",
                color = Color(0xFFD4AF37),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Rendimento Ultimi 7 Giorni",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Light
            )
        }

        WeeklyBarChart(transactions = transactions)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0F0F0F).copy(alpha = 0.7f))
                .border(1.dp, Color(0xFF1E1E1E), RoundedCornerShape(16.dp))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "TASSO DI RISPARMIO",
                    color = Color.Gray,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${String.format("%.1f", savingRate)}%",
                    color = Color(0xFFD4AF37),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Divider(
                modifier = Modifier
                    .height(36.dp)
                    .width(1.dp),
                color = Color(0xFF222222)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "TEMPO FOCUS",
                    color = Color.Gray,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                val hrs = focusMins.toInt() / 60
                val mins = focusMins.toInt() % 60
                Text(
                    text = if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Divider(
                modifier = Modifier
                    .height(36.dp)
                    .width(1.dp),
                color = Color(0xFF222222)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "DISTRAZIONE",
                    color = Color.Gray,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                val hrs = unlockMins.toInt() / 60
                val mins = unlockMins.toInt() % 60
                Text(
                    text = if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m",
                    color = Color.LightGray,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Text(
            text = if (savingRate >= 85.0) {
                "Tasso di disciplina eccellente. Ottimo autocontrollo."
            } else if (savingRate >= 60.0) {
                "Buona concentrazione, ma i social hanno intaccato il tuo saldo."
            } else {
                "Urge sessione di risparmio. Ricarica la tua carta con attività Focus."
            },
            color = Color.LightGray,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)
        )
    }
}

@Composable
fun WeeklyBarChart(transactions: List<Transaction>) {
    val days = listOf("Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom")
    val now = System.currentTimeMillis()
    val dayMs = 24 * 60 * 60 * 1000L
    
    val dailyFocus = DoubleArray(7)
    val dailyUnlock = DoubleArray(7)
    
    for (tx in transactions) {
        val ageDays = ((now - tx.timestamp) / dayMs).toInt()
        if (ageDays in 0..6) {
            val dayIndex = (6 - ageDays)
            if (tx.type == "DEPOSIT") {
                dailyFocus[dayIndex] += tx.amount * 10.0
            } else {
                dailyUnlock[dayIndex] += tx.amount
            }
        }
    }
    
    val maxVal = (dailyFocus.maxOrNull() ?: 1.0).coerceAtLeast(dailyUnlock.maxOrNull() ?: 1.0).coerceAtLeast(60.0)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(vertical = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0F0F0F).copy(alpha = 0.5f))
            .border(1.dp, Color(0xFF1E1E1E), RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        for (i in 0..6) {
            val focusVal = dailyFocus[i]
            val unlockVal = dailyUnlock[i]
            
            val focusHeight = (focusVal / maxVal * 110).toFloat().coerceAtLeast(3f)
            val unlockHeight = (unlockVal / maxVal * 110).toFloat().coerceAtLeast(3f)
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .height(110.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFF181818))
                    )

                    if (focusVal > 0 || unlockVal > 0) {
                        val totalHeight = (focusHeight + unlockHeight).coerceAtMost(110f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(totalHeight.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color(0xFFFF5252), Color(0xFFD4AF37)),
                                        startY = 0f,
                                        endY = totalHeight
                                    )
                                )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = days[i],
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

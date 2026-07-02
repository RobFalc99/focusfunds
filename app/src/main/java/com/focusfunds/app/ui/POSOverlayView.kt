package com.focusfunds.app.ui

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.focusfunds.app.data.FocusFundsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Synthesize C6 + G6 crystal chord sound
fun playSuccessSound() {
    Thread {
        val sampleRate = 44100
        val duration = 0.6
        val numSamples = (duration * sampleRate).toInt()
        val buffer = ShortArray(numSamples)
        
        val freq1 = 1046.50 // C6
        val freq2 = 1567.98 // G6
        
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val decay = Math.exp(-6.0 * t)
            val value = (Math.sin(2 * Math.PI * freq1 * t) * 0.6 + Math.sin(2 * Math.PI * freq2 * t) * 0.4) * decay
            buffer[i] = (value * 32767).toInt().toShort()
        }
        
        try {
            val audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                buffer.size * 2,
                AudioTrack.MODE_STATIC
            )
            audioTrack.write(buffer, 0, buffer.size)
            audioTrack.play()
            Thread.sleep(700)
            audioTrack.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }.start()
}

// Custom haptic vibration
fun triggerHaptics(context: Context, doubleVibrate: Boolean = false) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator != null && vibrator.hasVibrator()) {
            if (doubleVibrate) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val timings = longArrayOf(0, 80, 80, 80)
                    val amplitudes = intArrayOf(0, 180, 0, 180)
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 80, 80, 80), -1)
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(120)
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Composable
fun POSOverlayContent(
    packageName: String,
    appName: String,
    onUnlockSuccess: (minutes: Int) -> Unit,
    onEmergencyUnlock: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { FocusFundsRepository(context.applicationContext) }
    val walletState by repository.walletStateFlow.collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()
    
    var inputMinutes by remember { mutableStateOf("") }
    var showInsufficientDialog by remember { mutableStateOf(false) }
    var isApproving by remember { mutableStateOf(false) }
    var hasApproved by remember { mutableStateOf(false) }
    
    var cardOffsetY by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 150.dp.toPx() }
    
    val balance = walletState?.balance ?: 5.0
    val cost = inputMinutes.toIntOrNull()?.toDouble() ?: 0.0
    val currentTheme = walletState?.selectedTheme ?: 0

    // Radial gold wave when swiped/tapped
    val successWaveVal = remember { Animatable(0f) }
    LaunchedEffect(hasApproved) {
        if (hasApproved) {
            successWaveVal.animateTo(
                targetValue = 1f,
                animationSpec = tween(700, easing = LinearOutSlowInEasing)
            )
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF000000)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Shared Dynamic Luxury Background
            LuxuryDynamicBackground(theme = currentTheme)

            // Gold wave overlay
            if (hasApproved) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFD4AF37).copy(alpha = 0.25f * (1f - successWaveVal.value)),
                                    Color.Transparent
                                ),
                                center = Offset(
                                    x = density.run { 180.dp.toPx() },
                                    y = density.run { 120.dp.toPx() }
                                ),
                                radius = successWaveVal.value * density.run { 500.dp.toPx() }
                            )
                        )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header (Apple Wallet style)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ACCESSIBILITÀ LIMITATA",
                            color = Color(0xFFD4AF37),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = appName,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF1E1E1E).copy(alpha = 0.8f))
                            .clickable { onCancel() }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Annulla",
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // NFC terminal receptor mockup
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Canvas(modifier = Modifier.size(36.dp)) {
                        val strokeWidth = 3.dp.toPx()
                        val color = if (hasApproved) Color(0xFFD4AF37) else Color.Gray
                        drawCircle(color, radius = 3.dp.toPx(), center = Offset(18.dp.toPx(), 18.dp.toPx()))
                        drawArc(
                            color = color,
                            startAngle = -45f,
                            sweepAngle = 90f,
                            useCenter = false,
                            topLeft = Offset(11.dp.toPx(), 11.dp.toPx()),
                            size = Size(14.dp.toPx(), 14.dp.toPx()),
                            style = Stroke(strokeWidth)
                        )
                        drawArc(
                            color = color,
                            startAngle = -45f,
                            sweepAngle = 90f,
                            useCenter = false,
                            topLeft = Offset(6.dp.toPx(), 6.dp.toPx()),
                            size = Size(24.dp.toPx(), 24.dp.toPx()),
                            style = Stroke(strokeWidth)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (hasApproved) "PAGAMENTO APPROVATO" else "AVVICINA LA CARTA AL LETTORE",
                        color = if (hasApproved) Color(0xFFD4AF37) else Color.Gray,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                }

                // Terminal Display (Minimalist panel)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (inputMinutes.isEmpty()) "0" else inputMinutes,
                        color = Color.White,
                        fontSize = 54.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = FontFamily.SansSerif
                    )
                    Text(
                        text = "MINUTI RICHIESTI",
                        color = Color(0xFF8E8E93),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F0F0F).copy(alpha = 0.8f))
                            .border(1.dp, Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Costo: ${String.format("%.1f", cost)} FF",
                            color = if (cost > balance) Color(0xFFFF5252) else Color(0xFFD4AF37),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "|",
                            color = Color(0xFF222222),
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Saldo: ${String.format("%.2f", balance)} FF",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }

                // Quick presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(2, 5, 10, 15, 30).forEach { mins ->
                        val selected = inputMinutes == mins.toString()
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (selected) Color(0xFFD4AF37) else Color(0xFF111111))
                                .border(1.dp, if (selected) Color(0xFFD4AF37) else Color(0xFF222222), RoundedCornerShape(16.dp))
                                .clickable {
                                    inputMinutes = mins.toString()
                                    triggerHaptics(context)
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "${mins}m",
                                color = if (selected) Color.Black else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Numeric Keypad
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    val keys = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("C", "0", "⌫")
                    )
                    keys.forEach { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            row.forEach { key ->
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF0F0F0F).copy(alpha = 0.8f))
                                        .border(1.dp, Color(0xFF1F1F1F), CircleShape)
                                        .clickable {
                                            triggerHaptics(context)
                                            when (key) {
                                                "C" -> inputMinutes = ""
                                                "⌫" -> if (inputMinutes.isNotEmpty()) {
                                                    inputMinutes = inputMinutes.dropLast(1)
                                                }
                                                else -> {
                                                    if (inputMinutes.length < 3) {
                                                        inputMinutes += key
                                                    }
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = key,
                                        color = if (key == "C" || key == "⌫") Color(0xFFD4AF37) else Color.White,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Light
                                    )
                                }
                            }
                        }
                    }
                }

                // Themed Credit Card Swipe
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .width(280.dp)
                            .height(130.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF070707))
                            .border(1.dp, Color(0xFF1E1E1E), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Text(
                            text = "▲ TRASCINA LA CARTA VERSO L'ALTO ▲",
                            color = Color(0xFF444444),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }

                    // Draggable Box container for the themed credit card
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(0, cardOffsetY.toInt()) }
                            .pointerInput(isApproving || hasApproved) {
                                if (isApproving || hasApproved) return@pointerInput
                                detectDragGestures(
                                    onDragEnd = {
                                        if (cardOffsetY < -swipeThresholdPx && cost > 0) {
                                            isApproving = true
                                            coroutineScope.launch {
                                                if (balance >= cost) {
                                                    repository.deductFocusFunds(cost, "Unlocked $appName")
                                                    triggerHaptics(context, doubleVibrate = false)
                                                    playSuccessSound()
                                                    hasApproved = true
                                                    delay(900)
                                                    onUnlockSuccess(cost.toInt())
                                                } else {
                                                    triggerHaptics(context, doubleVibrate = true)
                                                    cardOffsetY = 0f
                                                    isApproving = false
                                                    showInsufficientDialog = true
                                                }
                                            }
                                        } else {
                                            cardOffsetY = 0f
                                        }
                                    },
                                    onDragCancel = {
                                        cardOffsetY = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        cardOffsetY = (cardOffsetY + dragAmount.y).coerceAtMost(0f)
                                    }
                                )
                            }
                    ) {
                        // Render unified themed credit card scaled down to 0.9f
                        LuxuryCreditCard(
                            balance = balance,
                            theme = currentTheme,
                            scale = 0.9f
                        )
                    }
                }
            }
        }
    }

    // Insufficient Funds Dialog
    if (showInsufficientDialog) {
        AlertDialog(
            onDismissRequest = { },
            containerColor = Color(0xFF0C0C0C),
            modifier = Modifier.border(1.dp, Color(0xFF1E1E1E), RoundedCornerShape(28.dp)),
            title = {
                Text(
                    text = "SALDO INSUFFICIENTE",
                    color = Color(0xFFFF5252),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            },
            text = {
                Text(
                    text = "Non hai abbastanza FF per effettuare questo sblocco. Puoi utilizzare uno \"Sblocco di Emergenza\" per ottenere 5 minuti di sblocco immediato. Il tuo saldo andrà in negativo e dovrai ripagarlo con sessioni di Focus.",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37)),
                    onClick = {
                        showInsufficientDialog = false
                        isApproving = true
                        coroutineScope.launch {
                            triggerHaptics(context, doubleVibrate = false)
                            playSuccessSound()
                            hasApproved = true
                            delay(900)
                            onEmergencyUnlock()
                        }
                    }
                ) {
                    Text("Sblocco Emergenza (-5.0 FF)", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showInsufficientDialog = false
                        onCancel()
                    }
                ) {
                    Text("Annulla ed Esci", color = Color.Gray)
                }
            }
        )
    }
}

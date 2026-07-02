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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

// Synthesize a high-pitched crystal "dling" sound (C6 + G6 chord with exponential decay)
fun playSuccessSound() {
    Thread {
        val sampleRate = 44100
        val duration = 0.6 // seconds
        val numSamples = (duration * sampleRate).toInt()
        val buffer = ShortArray(numSamples)
        
        val freq1 = 1046.50 // C6 note
        val freq2 = 1567.98 // G6 note
        
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

// Generate premium haptic vibration
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
                // Two short pulses for error
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val timings = longArrayOf(0, 80, 80, 80)
                    val amplitudes = intArrayOf(0, 180, 0, 180)
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 80, 80, 80), -1)
                }
            } else {
                // One single sharp pulse for approval
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
    
    // Swipe animation state
    var cardOffsetY by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 130.dp.toPx() }
    
    val balance = walletState?.balance ?: 5.0
    val cost = inputMinutes.toIntOrNull()?.toDouble() ?: 0.0

    // Card border glow animation
    val infiniteTransition = rememberInfiniteTransition(label = "gold_border")
    val borderRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val luxuryBorderBrush = Brush.sweepGradient(
        colors = listOf(
            Color(0xFFD4AF37), // Gold
            Color(0xFFC0C0C0), // Platinum
            Color(0xFF996515), // Dark Gold
            Color(0xFFF3E5AB), // Mellow Gold
            Color(0xFFD4AF37)
        )
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF000000) // True Black OLED
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "FOCUS FUNDS POS",
                        color = Color(0xFFD4AF37),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "Transazione Richiesta",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Light
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E1E1E))
                        .clickable { onCancel() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "ANNULLA",
                        color = Color.LightGray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            // Terminal Display
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0D0D0D))
                    .border(1.dp, Color(0xFF1F1F1F), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = appName.uppercase(),
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (inputMinutes.isEmpty()) "0" else inputMinutes,
                    color = Color.White,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "MINUTI RICHIESTI",
                    color = Color(0xFF8E8E93),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Divider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = Color(0xFF1E1E1E)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Costo: ${String.format("%.2f", cost)} FF",
                        color = if (cost > balance) Color(0xFFCF6679) else Color(0xFFD4AF37),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Saldo: ${String.format("%.2f", balance)} FF",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
            }

            // Presets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf(2, 5, 10, 15, 30).forEach { mins ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (inputMinutes == mins.toString()) Color(0xFFD4AF37) else Color(0xFF1E1E1E))
                            .clickable {
                                inputMinutes = mins.toString()
                                triggerHaptics(context)
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "${mins}m",
                            color = if (inputMinutes == mins.toString()) Color.Black else Color.White,
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
                    .padding(horizontal = 20.dp)
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
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        row.forEach { key ->
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(32.dp))
                                    .background(Color(0xFF0F0F0F))
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
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // Swipe Credit Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                if (!isApproving && !hasApproved) {
                    Text(
                        text = "↑ TRASCINA LA CARTA PER PAGARE ↑",
                        color = Color(0xFF6E6E73),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 10.dp)
                    )
                }

                // Black Card
                Box(
                    modifier = Modifier
                        .offset { IntOffset(0, cardOffsetY.toInt()) }
                        .width(260.dp)
                        .height(120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF1E1E1E), Color(0xFF050505))
                            )
                        )
                        .border(
                            width = 1.5.dp,
                            brush = luxuryBorderBrush,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .pointerInput(isApproving || hasApproved) {
                            if (isApproving || hasApproved) return@pointerInput
                            detectDragGestures(
                                onDragEnd = {
                                    if (cardOffsetY < -swipeThresholdPx && cost > 0) {
                                        isApproving = true
                                        coroutineScope.launch {
                                            if (balance >= cost) {
                                                // Success Flow
                                                repository.deductFocusFunds(cost, "Unlocked $appName")
                                                triggerHaptics(context, doubleVibrate = false)
                                                playSuccessSound()
                                                hasApproved = true
                                                delay(800)
                                                onUnlockSuccess(cost.toInt())
                                            } else {
                                                // Failure Flow
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
                        .padding(16.dp)
                ) {
                    // Inside Card Design
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
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            // Elegant "F" logo
                            Text(
                                text = "F",
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif
                            )
                        }

                        // Gold Chip
                        Box(
                            modifier = Modifier
                                .size(24.dp, 18.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(Color(0xFFD4AF37), Color(0xFFF3E5AB))
                                    )
                                )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                text = "L'OTTIMIZZATORE",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Light,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "CLASSIC CARD",
                                color = Color(0xFF8E8E93),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    // Insufficient Funds Dialog (Emergency Unlock)
    if (showInsufficientDialog) {
        AlertDialog(
            onDismissRequest = { /* Force choice */ },
            containerColor = Color(0xFF0F0F0F),
            title = {
                Text(
                    text = "SALDO INSUFFICIENTE",
                    color = Color(0xFFCF6679),
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
                            delay(800)
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
                    Text("Esci", color = Color.Gray)
                }
            }
        )
    }
}

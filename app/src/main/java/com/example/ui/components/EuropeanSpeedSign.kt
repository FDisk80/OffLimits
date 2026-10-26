package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AmoledSignStyle
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// Authentic International Traffic Sign Colors
val SpeedSignRed = Color(0xFFE51B24)
val SpeedSignRedAlert = Color(0xFFFF2233)
val SpeedSignWhite = Color(0xFFFFFFFF)
val SpeedSignBlack = Color(0xFF111111)
val SpeedSignGreyStripes = Color(0xFF333333)

@Composable
fun EuropeanSpeedSign(
    speedLimitKmH: Int,
    isUnlimited: Boolean = false,
    isSpeeding: Boolean = false,
    speedDeltaKmH: Double = 0.0,
    sizeDp: Dp = 310.dp,
    offsetX: Float = 0f,
    offsetY: Float = 0f,
    unitLabel: String = "km/h",
    amoledStyle: AmoledSignStyle = AmoledSignStyle.PIXEL_MESH,
    pixelIntervalSeconds: Int = 20,
    modifier: Modifier = Modifier
) {
    SpeedLimitSign(
        speedLimitValue = speedLimitKmH,
        isUnlimited = isUnlimited,
        isSpeeding = isSpeeding,
        speedDelta = speedDeltaKmH,
        sizeDp = sizeDp,
        offsetX = offsetX,
        offsetY = offsetY,
        unitLabel = unitLabel,
        amoledStyle = amoledStyle,
        pixelIntervalSeconds = pixelIntervalSeconds,
        modifier = modifier
    )
}

@Composable
fun SpeedLimitSign(
    speedLimitValue: Int,
    isUnlimited: Boolean = false,
    isSpeeding: Boolean = false,
    speedDelta: Double = 0.0,
    sizeDp: Dp = 310.dp,
    offsetX: Float = 0f,
    offsetY: Float = 0f,
    unitLabel: String = "km/h",
    amoledStyle: AmoledSignStyle = AmoledSignStyle.PIXEL_MESH,
    pixelIntervalSeconds: Int = 20,
    modifier: Modifier = Modifier
) {
    // Dynamic typography scaling based on sign diameter and digit count
    val digitsCount = speedLimitValue.toString().length
    val calculatedFontSize = when {
        isUnlimited -> (sizeDp.value * 0.16f).sp
        digitsCount >= 3 -> (sizeDp.value * 0.44f).sp // Fits 100, 110, 120, 130 with generous inner padding
        digitsCount == 2 -> (sizeDp.value * 0.54f).sp // Fits 30, 50, 70, 80 commanding and crisp
        else -> (sizeDp.value * 0.58f).sp             // Fits single digit (5, etc.)
    }

    // High-visibility animated pulse during overspeed
    val infiniteTransition = rememberInfiniteTransition(label = "speedHaloPulse")
    val haloPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.90f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloAlpha"
    )

    val currentBorderRed by animateColorAsState(
        targetValue = if (isSpeeding) SpeedSignRedAlert else SpeedSignRed,
        animationSpec = tween(durationMillis = 200),
        label = "signBorderColor"
    )

    // Ultra-sleek thinner authentic red border: ~5.5% of diameter
    val ringThickness = (sizeDp * 0.055f).coerceAtLeast(8.dp)

    // AMOLED Pixel Mesh phase alternation: flips every pixelIntervalSeconds (default 20s)
    var pixelPhase by remember { mutableIntStateOf(0) }
    LaunchedEffect(pixelIntervalSeconds, amoledStyle) {
        if (amoledStyle == AmoledSignStyle.PIXEL_MESH) {
            val intervalMs = (pixelIntervalSeconds.coerceAtLeast(5) * 1000L)
            while (true) {
                delay(intervalMs)
                pixelPhase = 1 - pixelPhase
            }
        }
    }

    val density = LocalDensity.current
    val cellSizePx = remember(density) {
        with(density) { 2.dp.toPx().coerceAtLeast(2f).toInt() }
    }

    // Create alternating subpixel checkerboard shaders for white and red areas
    val whiteShader = remember(pixelPhase, cellSizePx) {
        createCheckerboardShader(android.graphics.Color.WHITE, pixelPhase, cellSizePx)
    }
    val redBorderArgb = currentBorderRed.toArgb()
    val redShader = remember(pixelPhase, cellSizePx, redBorderArgb) {
        createCheckerboardShader(redBorderArgb, pixelPhase, cellSizePx)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .testTag("speed_limit_sign_container")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(sizeDp)
                .drawBehind {
                    if (isSpeeding) {
                        drawIntoCanvas { canvas ->
                            val paint = Paint().asFrameworkPaint().apply {
                                color = android.graphics.Color.parseColor("#FF1744")
                                setShadowLayer(
                                    sizeDp.toPx() * 0.20f,
                                    0f,
                                    0f,
                                    android.graphics.Color.argb((haloPulseAlpha * 255).toInt(), 255, 23, 68)
                                )
                            }
                            canvas.drawCircle(
                                Offset(size.width / 2f, size.height / 2f),
                                size.width / 2f,
                                Paint().apply { asFrameworkPaint().set(paint) }
                            )
                        }
                    }
                }
        ) {
            // Main sign body rendered with active AMOLED Protection
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("speed_limit_sign")
            ) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val strokeWidthPx = ringThickness.toPx()
                val innerRadius = (size.width / 2f) - (strokeWidthPx / 2f)

                when (amoledStyle) {
                    AmoledSignStyle.PIXEL_MESH -> {
                        // 1. Draw interior with 50% pitch-black OLED subpixel mesh
                        drawIntoCanvas { canvas ->
                            val fillPaint = android.graphics.Paint().apply {
                                shader = whiteShader
                                isAntiAlias = true
                            }
                            canvas.nativeCanvas.drawCircle(
                                center.x,
                                center.y,
                                innerRadius,
                                fillPaint
                            )
                        }

                        // 2. Draw red border ring with matching alternating mesh
                        drawIntoCanvas { canvas ->
                            val strokePaint = android.graphics.Paint().apply {
                                shader = redShader
                                isAntiAlias = true
                                style = android.graphics.Paint.Style.STROKE
                                this.strokeWidth = strokeWidthPx
                            }
                            canvas.nativeCanvas.drawCircle(
                                center.x,
                                center.y,
                                innerRadius,
                                strokePaint
                            )
                        }
                    }

                    AmoledSignStyle.STEALTH_STENCIL -> {
                        // 1. Hollow pure pitch-black AMOLED interior (#000000 -> 100% OLED diodes OFF)
                        drawCircle(
                            color = Color.Black,
                            radius = innerRadius,
                            center = center
                        )

                        // 2. Solid glowing outer red ring
                        drawCircle(
                            color = currentBorderRed,
                            radius = innerRadius,
                            center = center,
                            style = Stroke(width = strokeWidthPx)
                        )
                    }

                    AmoledSignStyle.SOLID_CLASSIC -> {
                        // 1. Solid authentic European white fill
                        drawCircle(
                            color = SpeedSignWhite,
                            radius = innerRadius,
                            center = center
                        )

                        // 2. Solid authentic red ring
                        drawCircle(
                            color = currentBorderRed,
                            radius = innerRadius,
                            center = center,
                            style = Stroke(width = strokeWidthPx)
                        )
                    }
                }
            }

            if (isUnlimited) {
                // Derestriction / End of limit diagonal grey stripes
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeW = 4.dp.toPx()
                    val spacing = 12.dp.toPx()
                    for (i in -2..2) {
                        val offset = i * spacing
                        drawLine(
                            color = SpeedSignGreyStripes,
                            start = Offset(size.width * 0.15f + offset, size.height * 0.85f),
                            end = Offset(size.width * 0.85f + offset, size.height * 0.15f),
                            strokeWidth = strokeW
                        )
                    }
                }
            } else {
                // Centered number display with safe inner padding
                // In STEALTH_STENCIL mode, digits glow pure white (#FFFFFF) against black; otherwise crisp black (#111111)
                val numberColor = if (amoledStyle == AmoledSignStyle.STEALTH_STENCIL) Color.White else SpeedSignBlack

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(sizeDp * 0.08f)
                ) {
                    Text(
                        text = speedLimitValue.toString(),
                        color = numberColor,
                        fontSize = calculatedFontSize,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif,
                        textAlign = TextAlign.Center,
                        lineHeight = calculatedFontSize,
                        letterSpacing = if (digitsCount >= 3) (-2).sp else (-0.5).sp,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.testTag("speed_limit_number")
                    )
                }
            }
        }

        // Subtle speed unit indicator
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isUnlimited) "NO SPEED LIMIT" else "SPEED LIMIT ($unitLabel)",
            color = Color(0xFF64748B),
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            letterSpacing = 0.5.sp
        )
    }
}

/**
 * Creates a hardware-accelerated 2x2 cell checkerboard BitmapShader.
 * In Phase 0: even pixels are illuminated, odd pixels are pure pitch-black (#000000).
 * In Phase 1: even pixels are pure pitch-black (#000000), odd pixels are illuminated.
 * On AMOLED displays, black pixels emit 0 nits and draw 0 current, giving diodes 50% rest time.
 */
private fun createCheckerboardShader(colorArgb: Int, phase: Int, cellSizePx: Int): BitmapShader {
    val size = (cellSizePx * 2).coerceAtLeast(2)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)

    // Pitch black background (OLED pixels OFF)
    canvas.drawColor(android.graphics.Color.BLACK)

    val paint = android.graphics.Paint().apply {
        isAntiAlias = false
        color = colorArgb
    }

    val half = cellSizePx.toFloat()
    val full = size.toFloat()

    if (phase == 0) {
        // (0,0) and (1,1) illuminated
        canvas.drawRect(0f, 0f, half, half, paint)
        canvas.drawRect(half, half, full, full, paint)
    } else {
        // (1,0) and (0,1) illuminated
        canvas.drawRect(half, 0f, full, half, paint)
        canvas.drawRect(0f, half, half, full, paint)
    }

    return BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
}

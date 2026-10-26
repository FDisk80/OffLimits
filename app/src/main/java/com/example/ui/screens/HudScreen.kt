package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.DrivingState
import com.example.data.model.HudSettings
import com.example.data.model.SpeedUnit
import com.example.ui.components.SpeedLimitSign
import com.example.ui.theme.AmoledBlack
import com.example.ui.theme.NeonCyan
import com.example.viewmodel.SpeedHudViewModel
import kotlin.math.roundToInt

@Composable
fun HudScreen(
    viewModel: SpeedHudViewModel,
    drivingState: DrivingState,
    settings: HudSettings,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var showManualOverrideDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.switchToLiveGps()
        }
    }

    // Automatically ensure live GPS permissions on launch
    LaunchedEffect(Unit) {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            viewModel.switchToLiveGps()
        }
    }

    // Smooth drift animation for AMOLED burn-in protection
    val animatedOffsetX by animateFloatAsState(
        targetValue = if (settings.amoledProtectionEnabled) drivingState.amoledOffsetX else 0f,
        animationSpec = tween(durationMillis = 3500, easing = FastOutSlowInEasing),
        label = "burnInOffsetX"
    )
    val animatedOffsetY by animateFloatAsState(
        targetValue = if (settings.amoledProtectionEnabled) drivingState.amoledOffsetY else 0f,
        animationSpec = tween(durationMillis = 3500, easing = FastOutSlowInEasing),
        label = "burnInOffsetY"
    )

    // Unit-accurate driving values
    val displayedCurrentSpeed = drivingState.currentSpeedDisplay
    val displayedSpeedLimit = drivingState.displayedSpeedLimit
    val displayedDelta = drivingState.speedDeltaKmH

    val speedTextColor by animateColorAsState(
        targetValue = if (drivingState.isSpeeding) Color(0xFFFF2233) else Color.White,
        animationSpec = tween(250),
        label = "speedColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "edgeWarningPulse")
    val edgeBorderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "borderPulse"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .scale(scaleX = if (settings.hudMirrorMode) -1f else 1f, scaleY = 1f)
            .alpha(settings.nightDimLevel)
            .testTag("hud_screen")
    ) {
        // Red edge glow perimeter when speeding
        if (drivingState.isSpeeding) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = 5.dp,
                        color = Color(0xFFFF1744).copy(alpha = edgeBorderAlpha)
                    )
            )
        }

        if (isLandscape) {
            // ==========================================
            // LANDSCAPE MODE: TWO-COLUMN DASHBOARD LAYOUT
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Column: Commanding Speed Limit Sign
                Box(
                    modifier = Modifier
                        .weight(1.0f)
                        .fillMaxHeight()
                        .offset { IntOffset(animatedOffsetX.roundToInt(), animatedOffsetY.roundToInt()) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        SpeedLimitSign(
                            speedLimitValue = displayedSpeedLimit,
                            isUnlimited = drivingState.isUnlimited,
                            isSpeeding = drivingState.isSpeeding,
                            speedDelta = displayedDelta,
                            sizeDp = 205.dp,
                            unitLabel = settings.speedUnit.label,
                            amoledStyle = settings.amoledSignStyle,
                            pixelIntervalSeconds = settings.pixelAlternationIntervalSeconds,
                            modifier = Modifier.clickable { showManualOverrideDialog = true }
                        )

                        if (settings.manualSpeedLimitOverride != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF0F172A))
                                    .border(1.dp, NeonCyan, RoundedCornerShape(16.dp))
                                    .clickable { viewModel.setManualSpeedLimit(null) }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.GpsFixed,
                                        contentDescription = "Reset to GPS",
                                        tint = NeonCyan,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Manual Limit • Tap for GPS",
                                        color = NeonCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Right Column: Top Bar, Vehicle Speedometer, and Transparent Trip Stats
                Column(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top controls row (GPS status and uncolored action buttons)
                    HudTopBar(
                        isSimulated = drivingState.isSimulated,
                        gpsStatusMessage = drivingState.gpsStatusMessage,
                        silentMode = settings.silentMode,
                        hudMirrorMode = settings.hudMirrorMode,
                        animatedOffsetX = animatedOffsetX,
                        animatedOffsetY = animatedOffsetY,
                        onToggleSilent = { viewModel.toggleSilentMode() },
                        onToggleMirror = { viewModel.toggleHudMirrorMode() }
                    )

                    // Vehicle Speedometer
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.offset {
                            IntOffset(
                                (animatedOffsetX * 0.20f).roundToInt(),
                                (animatedOffsetY * 0.20f).roundToInt()
                            )
                        }
                    ) {
                        Text(
                            text = "${displayedCurrentSpeed.roundToInt()}",
                            color = speedTextColor,
                            fontSize = 72.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.SansSerif,
                            lineHeight = 72.sp,
                            modifier = Modifier.testTag("current_speed_value")
                        )
                        Text(
                            text = settings.speedUnit.label.uppercase(),
                            color = if (drivingState.isSpeeding) Color(0xFFFF6B6B) else NeonCyan,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 2.sp
                        )
                    }

                    // Pure Transparent Trip Statistics (Floating cleanly without grayish background)
                    HudTripStatsRow(
                        drivingState = drivingState,
                        settings = settings,
                        animatedOffsetX = animatedOffsetX,
                        animatedOffsetY = animatedOffsetY,
                        onResetTrip = { viewModel.resetTrip() }
                    )
                }
            }
        } else {
            // ==========================================
            // PORTRAIT MODE: COMMANDING VERTICAL CLUSTER
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top status bar
                HudTopBar(
                    isSimulated = drivingState.isSimulated,
                    gpsStatusMessage = drivingState.gpsStatusMessage,
                    silentMode = settings.silentMode,
                    hudMirrorMode = settings.hudMirrorMode,
                    animatedOffsetX = animatedOffsetX,
                    animatedOffsetY = animatedOffsetY,
                    onToggleSilent = { viewModel.toggleSilentMode() },
                    onToggleMirror = { viewModel.toggleHudMirrorMode() }
                )

                // Main Speed Sign & Vehicle Speedometer
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(animatedOffsetX.roundToInt(), animatedOffsetY.roundToInt()) }
                        .testTag("hud_main_display_cluster")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        SpeedLimitSign(
                            speedLimitValue = displayedSpeedLimit,
                            isUnlimited = drivingState.isUnlimited,
                            isSpeeding = drivingState.isSpeeding,
                            speedDelta = displayedDelta,
                            sizeDp = 300.dp,
                            unitLabel = settings.speedUnit.label,
                            amoledStyle = settings.amoledSignStyle,
                            pixelIntervalSeconds = settings.pixelAlternationIntervalSeconds,
                            modifier = Modifier.clickable {
                                showManualOverrideDialog = true
                            }
                        )

                        if (settings.manualSpeedLimitOverride != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF0F172A))
                                    .border(1.dp, NeonCyan, RoundedCornerShape(16.dp))
                                    .clickable { viewModel.setManualSpeedLimit(null) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.GpsFixed,
                                        contentDescription = "Reset to GPS",
                                        tint = NeonCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Manual Limit Active • Tap to Reset to GPS",
                                        color = NeonCyan,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Current Vehicle Speed Readout
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.offset {
                                IntOffset(
                                    (animatedOffsetX * 0.15f).roundToInt(),
                                    (animatedOffsetY * 0.15f).roundToInt()
                                )
                            }
                        ) {
                            Text(
                                text = "${displayedCurrentSpeed.roundToInt()}",
                                color = speedTextColor,
                                fontSize = 84.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.SansSerif,
                                lineHeight = 84.sp,
                                modifier = Modifier.testTag("current_speed_value")
                            )
                            Text(
                                text = settings.speedUnit.label.uppercase(),
                                color = if (drivingState.isSpeeding) Color(0xFFFF6B6B) else NeonCyan,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 2.sp
                            )
                        }
                    }
                }

                // Pure Transparent Trip Statistics (Floating cleanly without grayish background)
                HudTripStatsRow(
                    drivingState = drivingState,
                    settings = settings,
                    animatedOffsetX = animatedOffsetX,
                    animatedOffsetY = animatedOffsetY,
                    onResetTrip = { viewModel.resetTrip() }
                )
            }
        }
    }

    // Manual Speed Limit Override Dialog (Dynamic MPH vs KM/H)
    if (showManualOverrideDialog) {
        val unitLabel = settings.speedUnit.label
        val isMph = settings.speedUnit == SpeedUnit.MPH
        val speeds = if (isMph) {
            listOf(15, 20, 25, 30, 35, 45, 55, 60, 65, 70, 75, 80)
        } else {
            listOf(30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130)
        }

        val columns = if (isLandscape) 6 else 4
        val chunkedRows = speeds.chunked(columns)

        AlertDialog(
            onDismissRequest = { showManualOverrideDialog = false },
            title = {
                Text(
                    text = "Select Speed Limit ($unitLabel)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    maxLines = 1
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    // Top Prominent Reset Button - ALWAYS visible in Landscape & Portrait
                    Button(
                        onClick = {
                            viewModel.setManualSpeedLimit(null)
                            showManualOverrideDialog = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("reset_to_gps_limit_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (settings.manualSpeedLimitOverride != null) NeonCyan else Color(0xFF1E293B)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GpsFixed,
                            contentDescription = "Reset to GPS",
                            tint = if (settings.manualSpeedLimitOverride != null) Color.Black else NeonCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Reset to Automatic GPS Limit",
                            color = if (settings.manualSpeedLimitOverride != null) Color.Black else NeonCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Or choose manual limit in $unitLabel:",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    chunkedRows.forEach { rowSpeeds ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowSpeeds.forEach { spd ->
                                val isSelected = drivingState.displayedSpeedLimit == spd && settings.manualSpeedLimitOverride != null
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(if (isLandscape) 38.dp else 42.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) NeonCyan else Color(0xFF1E293B))
                                        .clickable {
                                            viewModel.setManualSpeedLimit(spd)
                                            showManualOverrideDialog = false
                                        }
                                ) {
                                    Text(
                                        text = "$spd",
                                        color = if (isSelected) Color.Black else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                }
            },
            confirmButton = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (settings.manualSpeedLimitOverride != null) {
                        TextButton(
                            onClick = {
                                viewModel.setManualSpeedLimit(null)
                                showManualOverrideDialog = false
                            }
                        ) {
                            Text("Reset to GPS", color = NeonCyan, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    TextButton(onClick = { showManualOverrideDialog = false }) {
                        Text("Close", color = Color(0xFF94A3B8))
                    }
                }
            },
            containerColor = Color(0xFF0F172A)
        )
    }
}

@Composable
private fun HudTopBar(
    isSimulated: Boolean,
    gpsStatusMessage: String,
    silentMode: Boolean,
    hudMirrorMode: Boolean,
    animatedOffsetX: Float,
    animatedOffsetY: Float,
    onToggleSilent: () -> Unit,
    onToggleMirror: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset {
                IntOffset(
                    (animatedOffsetX * 0.70f).roundToInt(),
                    (animatedOffsetY * 0.60f).roundToInt()
                )
            }
            .testTag("hud_top_bar"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Top Left: Clean, minimal GPS Lock & Direction indicator (Street names removed per user request)
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isSimulated) Color(0xFFF59E0B) else Color(0xFF10B981))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = gpsStatusMessage,
                color = Color(0xFFE2E8F0),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }

        // Top right buttons: Pure uncolored background (floating directly on AMOLED black)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onToggleSilent,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("toggle_silent_mode")
            ) {
                Icon(
                    imageVector = if (silentMode) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = if (silentMode) "Unmute alerts" else "Mute alerts",
                    tint = if (silentMode) Color(0xFFEF4444) else Color(0xFF94A3B8)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
                onClick = onToggleMirror,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("toggle_hud_mirror")
            ) {
                Icon(
                    imageVector = Icons.Default.Flip,
                    contentDescription = "HUD Windshield Mirror",
                    tint = if (hudMirrorMode) NeonCyan else Color(0xFF94A3B8)
                )
            }
        }
    }
}

@Composable
private fun HudTripStatsRow(
    drivingState: DrivingState,
    settings: HudSettings,
    animatedOffsetX: Float,
    animatedOffsetY: Float,
    onResetTrip: () -> Unit
) {
    // Pure Transparent Background - Floating cleanly on OLED Black without any grayish card
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset {
                IntOffset(
                    (animatedOffsetX * 0.75f).roundToInt(),
                    (animatedOffsetY * 0.75f).roundToInt()
                )
            }
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("trip_stats_cluster"),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val distKm = drivingState.tripDistanceMeters / 1000.0
        TripStatItem(
            title = "TRIP",
            value = String.format("%.1f", if (settings.speedUnit == SpeedUnit.KMH) distKm else distKm * 0.621371),
            unit = if (settings.speedUnit == SpeedUnit.KMH) "km" else "mi"
        )

        val maxSpd = if (settings.speedUnit == SpeedUnit.KMH) drivingState.maxSpeedKmH else drivingState.maxSpeedKmH * 0.621371
        TripStatItem(
            title = "TOP SPEED",
            value = "${maxSpd.roundToInt()}",
            unit = settings.speedUnit.label
        )

        val avgSpd = if (settings.speedUnit == SpeedUnit.KMH) drivingState.avgSpeedKmH else drivingState.avgSpeedKmH * 0.621371
        TripStatItem(
            title = "AVG SPEED",
            value = "${avgSpd.roundToInt()}",
            unit = settings.speedUnit.label
        )

        IconButton(
            onClick = onResetTrip,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Reset Trip",
                tint = Color(0xFF64748B),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun TripStatItem(
    title: String,
    value: String,
    unit: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            color = Color(0xFF64748B),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = unit,
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
    }
}

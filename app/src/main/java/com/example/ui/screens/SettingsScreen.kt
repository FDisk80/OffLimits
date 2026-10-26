package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import com.example.data.model.AmoledSignStyle
import com.example.data.model.HudSettings
import com.example.data.model.SpeedUnit
import com.example.ui.theme.AmoledBlack
import com.example.ui.theme.NeonCyan
import com.example.viewmodel.SpeedHudViewModel
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: HudSettings,
    viewModel: SpeedHudViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .testTag("settings_screen")
    ) {
        Text(
            text = "HUD & Safety Settings",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Speed regulations, AMOLED display preservation, & alert thresholds",
            color = Color(0xFF94A3B8),
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(18.dp))

        // 1. Alert Threshold
        SettingsSectionCard(title = "SPEED LIMIT ALERT THRESHOLD") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Alert when exceeding limit by:",
                    color = Color(0xFFE2E8F0),
                    fontSize = 14.sp
                )
                Text(
                    text = "+${settings.warningThresholdKmH} ${settings.speedUnit.label}",
                    color = NeonCyan,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp
                )
            }

            Slider(
                value = settings.warningThresholdKmH.toFloat(),
                onValueChange = { viewModel.setWarningThreshold(it.roundToInt()) },
                valueRange = 0f..20f,
                steps = 19,
                colors = SliderDefaults.colors(
                    thumbColor = NeonCyan,
                    activeTrackColor = NeonCyan,
                    inactiveTrackColor = Color(0xFF334155)
                )
            )
            Text(
                text = "Default tolerance threshold is 5 ${settings.speedUnit.label}. Chime and visual alerts fire when speed exceeds (limit + threshold).",
                color = Color(0xFF64748B),
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Audio & Silent Mode Options
        SettingsSectionCard(title = "AUDIBLE CHIME & VIBRATION") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Silent Mode (Mute Audio Chime)",
                        color = Color(0xFFE2E8F0),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Disables audio tone while keeping visual alert glowing",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = settings.silentMode,
                    onCheckedChange = { viewModel.toggleSilentMode() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFFEF4444)
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFF1E293B))
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Vibration Alerts",
                        color = Color(0xFFE2E8F0),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Vibrate device when exceeding legal speed limit",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = settings.hapticFeedbackEnabled,
                    onCheckedChange = { viewModel.setHapticFeedback(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = NeonCyan
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Screen Orientation Lock
        SettingsSectionCard(title = "SCREEN ORIENTATION") {
            Text(
                text = "Choose display orientation for your car phone mount:",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Auto (Gyroscope)
                OrientationPictureButton(
                    isSelected = settings.orientationLock == com.example.data.model.ScreenOrientationLock.AUTO,
                    onClick = { viewModel.setOrientationLock(com.example.data.model.ScreenOrientationLock.AUTO) },
                    icon = Icons.Default.ScreenRotation,
                    label = "Auto",
                    modifier = Modifier.weight(1f)
                )

                // Locked Portrait
                OrientationPictureButton(
                    isSelected = settings.orientationLock == com.example.data.model.ScreenOrientationLock.PORTRAIT,
                    onClick = { viewModel.setOrientationLock(com.example.data.model.ScreenOrientationLock.PORTRAIT) },
                    icon = Icons.Default.PhoneAndroid,
                    label = "Portrait",
                    modifier = Modifier.weight(1f)
                )

                // Locked Landscape
                OrientationPictureButton(
                    isSelected = settings.orientationLock == com.example.data.model.ScreenOrientationLock.LANDSCAPE,
                    onClick = { viewModel.setOrientationLock(com.example.data.model.ScreenOrientationLock.LANDSCAPE) },
                    icon = Icons.Default.PhoneAndroid,
                    rotateIconDegrees = 90f,
                    label = "Landscape",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. AMOLED Burn-in Protection
        SettingsSectionCard(title = "AMOLED BURN-IN PROTECTION") {
            Text(
                text = "Sign Diode Protection Mode",
                color = Color(0xFFE2E8F0),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Protects the large white and red sign areas from OLED burn-in by alternating dark subpixels or using hollow stencil.",
                color = Color(0xFF64748B),
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            AmoledSignStyle.values().forEach { style ->
                val isSelected = settings.amoledSignStyle == style
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) Color(0xFF0F2338) else Color(0xFF0B1322))
                        .border(
                            1.dp,
                            if (isSelected) NeonCyan else Color(0xFF1E293B),
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { viewModel.setAmoledSignStyle(style) }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = style.title,
                                color = if (isSelected) NeonCyan else Color(0xFFE2E8F0),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (isSelected) {
                                Text(
                                    text = "ACTIVE",
                                    color = NeonCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = style.description,
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (settings.amoledSignStyle == AmoledSignStyle.PIXEL_MESH) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pixel Alternation Rate: ${settings.pixelAlternationIntervalSeconds}s",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Slider(
                    value = settings.pixelAlternationIntervalSeconds.toFloat(),
                    onValueChange = { viewModel.setPixelAlternationInterval(it.roundToInt()) },
                    valueRange = 10f..60f,
                    steps = 5,
                    colors = SliderDefaults.colors(
                        thumbColor = NeonCyan,
                        activeTrackColor = NeonCyan,
                        inactiveTrackColor = Color(0xFF334155)
                    )
                )
                Text(
                    text = "Every ${settings.pixelAlternationIntervalSeconds}s, active and dark pixels swap roles so diodes get 50% rest time.",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFF1E293B))
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Coordinate Orbit Drift",
                        color = Color(0xFFE2E8F0),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Smoothly drifts the HUD elements by a few pixels in an orbit to prevent static edges",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = settings.amoledProtectionEnabled,
                    onCheckedChange = { viewModel.toggleAmoledProtection() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = NeonCyan
                    )
                )
            }

            if (settings.amoledProtectionEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Drift Interval: ${settings.amoledShiftIntervalSeconds}s",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp
                    )
                }

                Slider(
                    value = settings.amoledShiftIntervalSeconds.toFloat(),
                    onValueChange = { viewModel.setAmoledInterval(it.roundToInt()) },
                    valueRange = 10f..60f,
                    steps = 5,
                    colors = SliderDefaults.colors(
                        thumbColor = NeonCyan,
                        activeTrackColor = NeonCyan,
                        inactiveTrackColor = Color(0xFF334155)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4. Night Driving Comfort & Units
        SettingsSectionCard(title = "NIGHT DRIVING & UNITS") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Speed Unit",
                        color = Color(0xFFE2E8F0),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Speed measurement unit (km/h vs mph)",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                }
                Row {
                    androidx.compose.material3.FilterChip(
                        selected = settings.speedUnit == SpeedUnit.KMH,
                        onClick = { if (settings.speedUnit != SpeedUnit.KMH) viewModel.toggleSpeedUnit() },
                        label = { Text("km/h") }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    androidx.compose.material3.FilterChip(
                        selected = settings.speedUnit == SpeedUnit.MPH,
                        onClick = { if (settings.speedUnit != SpeedUnit.MPH) viewModel.toggleSpeedUnit() },
                        label = { Text("mph") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFF1E293B))
            Spacer(modifier = Modifier.height(10.dp))

            // Windshield reflection HUD mirror mode
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Windshield HUD Mirror",
                        color = Color(0xFFE2E8F0),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Inverts the display horizontally for crisp reflection when placed on the car dashboard",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = settings.hudMirrorMode,
                    onCheckedChange = { viewModel.toggleHudMirrorMode() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = NeonCyan
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFF1E293B))
            Spacer(modifier = Modifier.height(10.dp))

            // Night Dimmer
            Text(
                text = "Night HUD Dim Level (${(settings.nightDimLevel * 100).roundToInt()}%)",
                color = Color(0xFFCBD5E1),
                fontSize = 13.sp
            )
            Slider(
                value = settings.nightDimLevel,
                onValueChange = { viewModel.setNightDimLevel(it) },
                valueRange = 0.35f..1.0f,
                colors = SliderDefaults.colors(
                    thumbColor = NeonCyan,
                    activeTrackColor = NeonCyan,
                    inactiveTrackColor = Color(0xFF334155)
                )
            )
        }
    }
}

@Composable
fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0F172A))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text(
            text = title,
            color = Color(0xFF64748B),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun OrientationPictureButton(
    isSelected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    rotateIconDegrees: Float = 0f
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Color(0xFF1E293B) else Color(0xFF0F172A))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) NeonCyan else Color(0xFF334155),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) NeonCyan else Color(0xFF94A3B8),
                modifier = Modifier
                    .size(26.dp)
                    .rotate(rotateIconDegrees)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                color = if (isSelected) Color.White else Color(0xFF64748B),
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

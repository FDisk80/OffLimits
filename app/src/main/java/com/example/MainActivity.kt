package com.example

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.ui.screens.HudScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TripLogScreen
import com.example.ui.theme.AmoledBlack
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonCyan
import com.example.viewmodel.SpeedHudViewModel
import kotlinx.coroutines.delay

enum class AppTab(val title: String, val icon: ImageVector) {
    HUD("Drive HUD", Icons.Default.Navigation),
    LOG("Safety Log", Icons.Default.History),
    SETTINGS("Settings", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {
    private val viewModel: SpeedHudViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep screen awake while driving in speed limit HUD mode
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Extend into cutout area on Android P+ to eliminate letterboxing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        setContent {
            MyApplicationTheme {
                MainAppContent(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainAppContent(viewModel: SpeedHudViewModel) {
    var currentTab by remember { mutableStateOf(AppTab.HUD) }
    val drivingState by viewModel.drivingState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val speedingIncidents by viewModel.speedingIncidents.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(settings.orientationLock) {
        val activity = context as? android.app.Activity
        activity?.requestedOrientation = when (settings.orientationLock) {
            com.example.data.model.ScreenOrientationLock.AUTO -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR
            com.example.data.model.ScreenOrientationLock.PORTRAIT -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            com.example.data.model.ScreenOrientationLock.LANDSCAPE -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    // Auto-fade timer: hides bottom navigation after 5 seconds of untouched screen
    var isMenuVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(lastInteractionTime, currentTab) {
        isMenuVisible = true
        // Keep menu visible continuously on Settings and Safety Log screens
        if (currentTab == AppTab.HUD) {
            delay(5000)
            isMenuVisible = false
        }
    }

    // BackHandler returns to HUD if on secondary tab
    BackHandler(enabled = currentTab != AppTab.HUD) {
        currentTab = AppTab.HUD
        lastInteractionTime = System.currentTimeMillis()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack)
            // Non-intrusively monitors touch interactions using PointerEventPass.Initial
            // without consuming touch events so all child buttons, sliders, and gestures respond instantly
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        if (event.changes.any { it.pressed }) {
                            lastInteractionTime = System.currentTimeMillis()
                            isMenuVisible = true
                        }
                    }
                }
            }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = AmoledBlack,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                // Bottom navigation bar completely fades out when untouched to prevent burn-in
                AnimatedVisibility(
                    visible = isMenuVisible || currentTab != AppTab.HUD,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it }
                ) {
                    NavigationBar(
                        containerColor = Color(0xFF070B12),
                        contentColor = Color.White,
                        tonalElevation = 0.dp,
                        modifier = Modifier.testTag("app_navigation_bar")
                    ) {
                        AppTab.values().forEach { tab ->
                            val isSelected = currentTab == tab
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    currentTab = tab
                                    lastInteractionTime = System.currentTimeMillis()
                                },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.title
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.title,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.Black,
                                    selectedTextColor = NeonCyan,
                                    indicatorColor = NeonCyan,
                                    unselectedIconColor = Color(0xFF64748B),
                                    unselectedTextColor = Color(0xFF64748B)
                                ),
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(AmoledBlack)
            ) {
                when (currentTab) {
                    AppTab.HUD -> HudScreen(
                        viewModel = viewModel,
                        drivingState = drivingState,
                        settings = settings
                    )
                    AppTab.LOG -> TripLogScreen(
                        incidents = speedingIncidents,
                        speedUnit = settings.speedUnit,
                        onClearLogs = { viewModel.clearSpeedingLogs() }
                    )
                    AppTab.SETTINGS -> SettingsScreen(
                        settings = settings,
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

package org.akanework.gramophone.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.akanework.gramophone.desktop.api.DesktopNetworkClient
import org.akanework.gramophone.desktop.player.DesktopSalvationPlayer
import org.akanework.gramophone.desktop.settings.DesktopSettingsManager
import org.akanework.gramophone.desktop.theme.DesktopThemeManager
import org.akanework.gramophone.desktop.ui.*

fun main() {
    application {
        val player = remember { DesktopSalvationPlayer() }
        var currentTab by remember { mutableStateOf(DesktopNavTab.LIBRARY) }
        var isLyricsOpen by remember { mutableStateOf(false) }
        var isQueueOpen by remember { mutableStateOf(false) }
        var rightTab by remember { mutableStateOf(DesktopRightPanelTab.LYRICS) }

        var token by remember { mutableStateOf(DesktopNetworkClient.tokenStorage.getToken()) }
        var isLoginOpen by remember { mutableStateOf(token.isNullOrEmpty()) }

        val themeMode by DesktopSettingsManager.themeMode.collectAsState()
        val colorScheme = remember(themeMode) { DesktopThemeManager.getColorScheme(themeMode) }

        val windowState = rememberWindowState(
            placement = androidx.compose.ui.window.WindowPlacement.Maximized,
            width = 1280.dp,
            height = 800.dp
        )

        Window(
            onCloseRequest = ::exitApplication,
            title = "Salvation Music",
            state = windowState
        ) {
            LaunchedEffect(Unit) {
                window.minimumSize = java.awt.Dimension(1000, 600)
            }
            MaterialTheme(
                colorScheme = colorScheme
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        // Чистое математическое масштабирование всего интерфейса
                        // без вмешательства в системный скейлинг AWT/Wayland
                        val uiScale = 1.4f
                        
                        Box(
                            modifier = Modifier
                                .size(maxWidth / uiScale, maxHeight / uiScale)
                                .graphicsLayer {
                                scaleX = uiScale
                                scaleY = uiScale
                                transformOrigin = TransformOrigin(0f, 0f)
                            }
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                // Основной контент: левый рейл + центральный экран + правый сайдбар
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(bottom = 86.dp)
                                ) {
                                    DesktopNavigationRail(
                                        selectedTab = currentTab,
                                        onTabSelected = { currentTab = it }
                                    )

                                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                        when (currentTab) {
                                            DesktopNavTab.HOME -> DesktopHomeScreen(player = player)
                                            DesktopNavTab.SEARCH -> DesktopSearchScreen(player = player)
                                            DesktopNavTab.LIBRARY -> DesktopLibraryScreen(player = player)
                                            DesktopNavTab.SETTINGS -> DesktopSettingsScreen(
                                                onLogout = {
                                                    DesktopNetworkClient.tokenStorage.saveToken(null)
                                                    token = null
                                                    isLoginOpen = true
                                                },
                                                onOpenLogin = {
                                                    isLoginOpen = true
                                                }
                                            )
                                        }
                                    }

                                    // Правый сайдбар (Текст караоке / Очередь)
                                    AnimatedVisibility(
                                        visible = isLyricsOpen || isQueueOpen,
                                        enter = slideInHorizontally(initialOffsetX = { it }),
                                        exit = slideOutHorizontally(targetOffsetX = { it })
                                    ) {
                                        DesktopRightSidePanel(
                                            player = player,
                                            selectedTab = rightTab,
                                            onTabSelected = { rightTab = it },
                                            onClose = {
                                                isLyricsOpen = false
                                                isQueueOpen = false
                                            }
                                        )
                                    }
                                }

                                // Нижний десктопный плеер во всю ширину
                                DesktopBottomPlayerBar(
                                    player = player,
                                    isLyricsOpen = isLyricsOpen,
                                    isQueueOpen = isQueueOpen,
                                    onToggleLyrics = {
                                        if (isLyricsOpen) {
                                            isLyricsOpen = false
                                        } else {
                                            isLyricsOpen = true
                                            isQueueOpen = false
                                            rightTab = DesktopRightPanelTab.LYRICS
                                        }
                                    },
                                    onToggleQueue = {
                                        if (isQueueOpen) {
                                            isQueueOpen = false
                                        } else {
                                            isQueueOpen = true
                                            isLyricsOpen = false
                                            rightTab = DesktopRightPanelTab.QUEUE
                                        }
                                    },
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                )

                                // Окно авторизации
                                if (isLoginOpen) {
                                    DesktopLoginDialog(
                                        onLoginSuccess = {
                                            token = DesktopNetworkClient.tokenStorage.getToken()
                                            isLoginOpen = false
                                            currentTab = DesktopNavTab.LIBRARY
                                        },
                                        onDismiss = {
                                            isLoginOpen = false
                                        }
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

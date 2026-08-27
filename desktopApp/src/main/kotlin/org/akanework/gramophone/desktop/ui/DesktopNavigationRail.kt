package org.akanework.gramophone.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class DesktopNavTab(val title: String, val icon: ImageVector) {
    HOME("Главная", Icons.Rounded.Home),
    SEARCH("Поиск", Icons.Rounded.Search),
    LIBRARY("Медиатека", Icons.AutoMirrored.Rounded.QueueMusic),
    SETTINGS("Настройки", Icons.Rounded.Settings)
}

@Composable
fun DesktopNavigationRail(
    selectedTab: DesktopNavTab,
    onTabSelected: (DesktopNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = modifier
            .width(84.dp)
            .fillMaxHeight(),
        color = colors.surfaceContainerLow,
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, colors.outlineVariant.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Логотип Salvation
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.primaryContainer.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Album,
                    contentDescription = "Salvation",
                    tint = colors.primary,
                    modifier = Modifier.size(26.dp)
                )
            }

            // 2. Основные вкладки
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                listOf(DesktopNavTab.HOME, DesktopNavTab.SEARCH, DesktopNavTab.LIBRARY).forEach { tab ->
                    val isSelected = selectedTab == tab
                    val tabBg = if (isSelected) colors.primaryContainer else Color.Transparent
                    val tabColor = if (isSelected) colors.onPrimaryContainer else colors.onSurfaceVariant

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onTabSelected(tab) }
                            .padding(vertical = 6.dp, horizontal = 12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 56.dp, height = 32.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(tabBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                tint = tabColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = tab.title,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = tabColor
                        )
                    }
                }
            }

            // 3. Настройки
            val isSettingsSelected = selectedTab == DesktopNavTab.SETTINGS
            IconButton(
                onClick = { onTabSelected(DesktopNavTab.SETTINGS) },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isSettingsSelected) colors.primaryContainer else Color.Transparent)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Настройки",
                    tint = if (isSettingsSelected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

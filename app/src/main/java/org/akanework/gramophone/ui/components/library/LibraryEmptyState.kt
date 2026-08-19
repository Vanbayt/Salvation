package org.akanework.gramophone.ui.components.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

enum class LibraryTabType {
    SONGS, ALBUMS, PLAYLISTS, ARTISTS
}

@Composable
fun LibraryEmptyState(
    type: LibraryTabType,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val (icon, title, subtitle, actionText) = when (type) {
        LibraryTabType.SONGS -> Quadruple(
            Icons.Rounded.MusicNote,
            "Треки не найдены",
            "Добавляйте любимые треки в избранное или обновляйте медиатеку",
            "Обновить"
        )
        LibraryTabType.ALBUMS -> Quadruple(
            Icons.Rounded.Album,
            "Альбомы отсутствуют",
            "Сохраненные и любимые альбомы будут отображаться здесь",
            "Обновить"
        )
        LibraryTabType.PLAYLISTS -> Quadruple(
            Icons.AutoMirrored.Rounded.QueueMusic,
            "Нет плейлистов",
            "Создавайте свои уникальные сборники или импортируйте плейлисты",
            "Создать плейлист"
        )
        LibraryTabType.ARTISTS -> Quadruple(
            Icons.Rounded.Person,
            "Исполнители не найдены",
            "Подписывайтесь на любимых артистов, чтобы следить за их релизами",
            "Обновить"
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (onActionClick != null) {
                Spacer(modifier = Modifier.height(24.dp))
                FilledTonalButton(
                    onClick = onActionClick,
                    shape = CircleShape,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Text(
                        text = actionText,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

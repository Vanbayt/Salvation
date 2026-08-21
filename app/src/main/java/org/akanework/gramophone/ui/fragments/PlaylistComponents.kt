package org.akanework.gramophone.ui.fragments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.api.Playlist // <-- Вот он, наш Плейлист!

@Composable
fun PlaylistCover(
    playlist: Playlist,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f) // Всегда квадратный
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        when {
            // 1. Есть кастомная обложка пользователя
            !playlist.coverUrl.isNullOrEmpty() -> {
                AsyncImage(
                    model = playlist.coverUrl,
                    contentDescription = playlist.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // 2. Нет кастомной, но есть треки (рисуем коллаж)
            playlist.autoCovers.isNotEmpty() -> {
                val covers = playlist.autoCovers.take(4) // Берем максимум 4

                if (covers.size == 1) {
                    // Если только 1 трек - рисуем его на весь квадрат
                    AsyncImage(
                        model = covers[0],
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Рисуем сетку 2x2
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.weight(1f)) {
                            AsyncImage(model = covers[0], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                            AsyncImage(model = covers.getOrNull(1), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                        }
                        Row(modifier = Modifier.weight(1f)) {
                            AsyncImage(model = covers.getOrNull(2), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                            AsyncImage(model = covers.getOrNull(3), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
            }
            // 3. Плейлист абсолютно пустой
            else -> {
                Icon(
                    painter = painterResource(id = R.drawable.ic_library),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        // Золотистый угловой бейдж FLAC
        val isFlac = org.akanework.gramophone.logic.lossless.LosslessStateManager.isPlaylistLossless(playlist.id)
        if (isFlac) {
            androidx.compose.material3.Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
                shape = RoundedCornerShape(6.dp),
                color = androidx.compose.ui.graphics.Color(0xFF1E1B18).copy(alpha = 0.88f),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, androidx.compose.ui.graphics.Color(0xFFD4AF37).copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check_circle),
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color(0xFFD4AF37),
                        modifier = Modifier.size(9.dp)
                    )
                    androidx.compose.material3.Text(
                        text = "FLAC",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.5.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                            letterSpacing = 0.5.sp
                        ),
                        color = androidx.compose.ui.graphics.Color(0xFFD4AF37)
                    )
                }
            }
        }
    }
}
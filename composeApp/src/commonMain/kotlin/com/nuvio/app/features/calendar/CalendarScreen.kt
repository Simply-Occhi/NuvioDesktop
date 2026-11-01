package com.nuvio.app.features.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.i18n.localizedSeasonEpisodeCode
import com.nuvio.app.core.ui.NuvioAsyncImage as AsyncImage
import com.nuvio.app.core.ui.nuvio
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.calendar_empty_subtitle
import nuvio.composeapp.generated.resources.calendar_empty_title
import nuvio.composeapp.generated.resources.calendar_filter_30_days
import nuvio.composeapp.generated.resources.calendar_filter_7_days
import nuvio.composeapp.generated.resources.calendar_filter_all
import nuvio.composeapp.generated.resources.calendar_refresh
import nuvio.composeapp.generated.resources.calendar_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun CalendarScreen(
    modifier: Modifier = Modifier,
    topChromePadding: Dp = 0.dp,
    onPosterClick: (type: String, id: String) -> Unit,
) {
    val state by CalendarRepository.uiState.collectAsStateWithLifecycle()
    val tokens = MaterialTheme.nuvio
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (state.groups.isEmpty() && !state.isLoading) {
            CalendarRepository.refresh()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.colors.background)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = topChromePadding + 20.dp,
                bottom = 48.dp,
                start = 24.dp,
                end = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "calendar_header") {
                CalendarHeader(
                    state = state,
                    onFilterChange = { CalendarRepository.setFilter(it) },
                    onRefresh = { CalendarRepository.refresh() },
                )
            }

            if (state.isLoading && state.groups.isEmpty()) {
                item(key = "calendar_loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 64.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = tokens.colors.accent,
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp,
                        )
                    }
                }
            } else if (state.groups.isEmpty()) {
                item(key = "calendar_empty") {
                    CalendarEmptyState()
                }
            } else {
                state.groups.forEach { group ->
                    item(key = "group_header_${group.dateIso}") {
                        CalendarDayHeader(group = group)
                    }

                    items(
                        items = group.episodes,
                        key = { "${it.showId}_${it.episodeId}_${it.releaseDateIso}" },
                    ) { episode ->
                        CalendarEpisodeRow(
                            episode = episode,
                            onClick = { onPosterClick(episode.showType, episode.showId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarHeader(
    state: CalendarUiState,
    onFilterChange: (CalendarFilter) -> Unit,
    onRefresh: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val rotation by animateFloatAsState(
        targetValue = if (state.isLoading) 360f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "refresh_spin",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(Res.string.calendar_title),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = tokens.colors.textPrimary,
                    ),
                )
                if (state.trackedShowsCount > 0) {
                    Text(
                        text = "${state.totalEpisodesCount} uscite da ${state.trackedShowsCount} serie seguite",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = tokens.colors.textSecondary,
                        ),
                    )
                }
            }

            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f)),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(Res.string.calendar_refresh),
                    tint = tokens.colors.textPrimary,
                    modifier = Modifier.rotate(rotation),
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CalendarFilterChip(
                label = stringResource(Res.string.calendar_filter_all),
                selected = state.selectedFilter == CalendarFilter.ALL,
                onClick = { onFilterChange(CalendarFilter.ALL) },
            )
            CalendarFilterChip(
                label = stringResource(Res.string.calendar_filter_7_days),
                selected = state.selectedFilter == CalendarFilter.DAYS_7,
                onClick = { onFilterChange(CalendarFilter.DAYS_7) },
            )
            CalendarFilterChip(
                label = stringResource(Res.string.calendar_filter_30_days),
                selected = state.selectedFilter == CalendarFilter.DAYS_30,
                onClick = { onFilterChange(CalendarFilter.DAYS_30) },
            )
        }
    }
}

@Composable
private fun CalendarFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val backgroundColor = if (selected) tokens.colors.accent else Color.White.copy(alpha = 0.08f)
    val textColor = if (selected) Color.White else tokens.colors.textSecondary

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = textColor,
            ),
        )
    }
}

@Composable
private fun CalendarDayHeader(group: CalendarDayGroup) {
    val tokens = MaterialTheme.nuvio
    val isHighlighted = group.isToday || group.isTomorrow
    val dotColor = when {
        group.isToday -> tokens.colors.accent
        group.isTomorrow -> Color(0xFFF59E0B)
        else -> tokens.colors.textMuted
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor),
        )

        Text(
            text = group.label,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isHighlighted) tokens.colors.textPrimary else tokens.colors.textSecondary,
            ),
        )

        if (group.isToday) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(tokens.colors.accent.copy(alpha = 0.22f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "OGGI",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = tokens.colors.accent,
                    ),
                )
            }
        } else if (group.isTomorrow) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFF59E0B).copy(alpha = 0.22f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "DOMANI",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF59E0B),
                    ),
                )
            }
        }
    }
}

@Composable
private fun CalendarEpisodeRow(
    episode: CalendarEpisodeItem,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val surfaceColor = if (isHovered) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color(0xFF141416).copy(alpha = 0.60f)
    }

    val borderColor = if (isHovered) {
        tokens.colors.accent.copy(alpha = 0.5f)
    } else {
        Color.White.copy(alpha = 0.06f)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = surfaceColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Episode Thumbnail / Poster
            Box(
                modifier = Modifier
                    .size(width = 110.dp, height = 66.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                val imageUrl = episode.thumbnail ?: episode.showPoster
                if (!imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = episode.showTitle,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.DateRange,
                        contentDescription = null,
                        tint = tokens.colors.textMuted,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            // Episode Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = episode.showTitle,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = tokens.colors.textPrimary,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    val code = localizedSeasonEpisodeCode(episode.seasonNumber, episode.episodeNumber)
                    if (!code.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(tokens.colors.accent.copy(alpha = 0.16f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = code,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = tokens.colors.accent,
                                ),
                            )
                        }
                    }
                }

                if (!episode.episodeTitle.isNullOrBlank()) {
                    Text(
                        text = episode.episodeTitle,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = tokens.colors.textSecondary,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (!episode.overview.isNullOrBlank()) {
                    Text(
                        text = episode.overview,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = tokens.colors.textMuted,
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Relative days indicator
            if (episode.daysUntilRelease != null) {
                val badgeText = when (episode.daysUntilRelease) {
                    0 -> "OGGI"
                    1 -> "DOMANI"
                    in 2..Int.MAX_VALUE -> "+${episode.daysUntilRelease} gg"
                    else -> "${episode.daysUntilRelease} gg"
                }

                val badgeBg = when (episode.daysUntilRelease) {
                    0 -> tokens.colors.accent
                    1 -> Color(0xFFF59E0B)
                    else -> Color.White.copy(alpha = 0.08f)
                }

                val badgeFg = when (episode.daysUntilRelease) {
                    0, 1 -> Color.White
                    else -> tokens.colors.textSecondary
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeBg)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = badgeFg,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarEmptyState() {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 80.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.DateRange,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = tokens.colors.accent,
            )
        }

        Text(
            text = stringResource(Res.string.calendar_empty_title),
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = tokens.colors.textPrimary,
            ),
        )

        Text(
            text = stringResource(Res.string.calendar_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = tokens.colors.textMuted,
            ),
        )
    }
}

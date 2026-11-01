package com.nuvio.app.features.calendar

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.format.formatReleaseDateForDisplay
import com.nuvio.app.core.i18n.localizedSeasonEpisodeCode
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.core.ui.NuvioAsyncImage as AsyncImage
import com.nuvio.app.core.ui.nuvio
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.calendar_refresh
import org.jetbrains.compose.resources.stringResource

@Composable
fun CalendarScreen(
    modifier: Modifier = Modifier,
    topChromePadding: Dp = 0.dp,
    onPosterClick: (type: String, id: String) -> Unit,
) {
    val state by CalendarRepository.uiState.collectAsStateWithLifecycle()
    val tokens = MaterialTheme.nuvio

    val nowMs = remember { EpisodeReleaseDatePlatform.nowEpochMs() }
    val todayIso = remember(nowMs) {
        EpisodeReleaseDatePlatform.localIsoDateAtEpochMs(nowMs) ?: "2026-11-01"
    }

    // Always trigger background refresh so latest episodes and new releases appear automatically
    LaunchedEffect(Unit) {
        CalendarRepository.refresh()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.colors.background)
    ) {
        val isWideLayout = maxWidth >= 960.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topChromePadding + 12.dp, bottom = 24.dp, start = 24.dp, end = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Month navigation bar (Stremio style)
            CalendarMonthNavigationHeader(
                currentMonth = state.currentMonth,
                isRefreshing = state.isRefreshing,
                onMonthSelect = { CalendarRepository.selectMonth(it) },
                onTodayClick = {
                    val y = todayIso.substring(0, 4).toIntOrNull() ?: 2026
                    val m = todayIso.substring(5, 7).toIntOrNull() ?: 11
                    CalendarRepository.selectMonth(CalendarMonth(y, m))
                    CalendarRepository.selectDate(todayIso)
                },
                onRefresh = { CalendarRepository.refresh() },
            )

            if (state.isRefreshing) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = tokens.colors.accent,
                    trackColor = Color.Transparent,
                )
            }

            if (state.isLoading && state.allEpisodes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = tokens.colors.accent,
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp,
                    )
                }
            } else {
                if (isWideLayout) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        // Calendar Month Grid (Stremio Style)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            CalendarMonthGrid(
                                month = state.currentMonth,
                                todayIso = todayIso,
                                selectedDateIso = state.selectedDateIso,
                                episodesByDate = state.episodesByDate,
                                onDateSelected = { CalendarRepository.selectDate(it) },
                                onEpisodeClick = onPosterClick,
                            )
                        }

                        // Right Sidebar: Schedule / Day Agenda
                        Surface(
                            modifier = Modifier
                                .width(330.dp)
                                .fillMaxHeight(),
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0xFF131317).copy(alpha = 0.75f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                        ) {
                            CalendarAgendaSidebar(
                                month = state.currentMonth,
                                todayIso = todayIso,
                                selectedDateIso = state.selectedDateIso,
                                episodesByDate = state.episodesByDate,
                                allEpisodes = state.allEpisodes,
                                onEpisodeClick = onPosterClick,
                            )
                        }
                    }
                } else {
                    // Narrow layout: scrollable grid with agenda underneath
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        CalendarMonthGrid(
                            month = state.currentMonth,
                            todayIso = todayIso,
                            selectedDateIso = state.selectedDateIso,
                            episodesByDate = state.episodesByDate,
                            onDateSelected = { CalendarRepository.selectDate(it) },
                            onEpisodeClick = onPosterClick,
                        )

                        CalendarAgendaSidebar(
                            month = state.currentMonth,
                            todayIso = todayIso,
                            selectedDateIso = state.selectedDateIso,
                            episodesByDate = state.episodesByDate,
                            allEpisodes = state.allEpisodes,
                            onEpisodeClick = onPosterClick,
                            modifier = Modifier.height(400.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarMonthNavigationHeader(
    currentMonth: CalendarMonth,
    isRefreshing: Boolean,
    onMonthSelect: (CalendarMonth) -> Unit,
    onTodayClick: () -> Unit,
    onRefresh: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val prevMonth = CalendarDateUtils.previousMonth(currentMonth)
    val nextMonth = CalendarDateUtils.nextMonth(currentMonth)

    val spin by animateFloatAsState(
        targetValue = if (isRefreshing) 360f else 0f,
        animationSpec = tween(700),
        label = "spin_refresh",
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left: Jump to Today button
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.White.copy(alpha = 0.06f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.clickable(onClick = onTodayClick),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.CalendarToday,
                    contentDescription = null,
                    tint = tokens.colors.accent,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "Oggi",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.colors.textPrimary,
                    ),
                )
            }
        }

        // Center: Month Tabs (Previous - Current - Next) Stremio Style!
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(
                onClick = { onMonthSelect(prevMonth) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = "Mese precedente",
                    tint = tokens.colors.textSecondary,
                )
            }

            // Previous Month name
            Text(
                text = CalendarDateUtils.getMonthName(prevMonth.month),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    color = tokens.colors.textMuted,
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onMonthSelect(prevMonth) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )

            // Current Month name (bold, white, prominent)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "${CalendarDateUtils.getMonthName(currentMonth.month)} ${currentMonth.year}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    ),
                )
            }

            // Next Month name
            Text(
                text = CalendarDateUtils.getMonthName(nextMonth.month),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    color = tokens.colors.textMuted,
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onMonthSelect(nextMonth) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )

            IconButton(
                onClick = { onMonthSelect(nextMonth) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "Mese successivo",
                    tint = tokens.colors.textSecondary,
                )
            }
        }

        // Right: Refresh button
        IconButton(
            onClick = onRefresh,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f)),
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = stringResource(Res.string.calendar_refresh),
                tint = tokens.colors.textPrimary,
                modifier = Modifier.rotate(spin),
            )
        }
    }
}

@Composable
private fun CalendarMonthGrid(
    month: CalendarMonth,
    todayIso: String,
    selectedDateIso: String?,
    episodesByDate: Map<String, List<CalendarEpisodeItem>>,
    onDateSelected: (String) -> Unit,
    onEpisodeClick: (type: String, id: String) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val daysInMonth = CalendarDateUtils.daysInMonth(month.year, month.month)
    val firstDayOfWeek = CalendarDateUtils.dayOfWeek(month.year, month.month, 1)

    // Weekday labels
    val weekdays = listOf("Lunedì", "Martedì", "Mercoledì", "Giovedì", "Venerdì", "Sabato", "Domenica")

    val totalCells = ((firstDayOfWeek + daysInMonth + 6) / 7) * 7
    val rows = totalCells / 7

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF101014).copy(alpha = 0.60f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Weekdays Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            weekdays.forEach { dayName ->
                Text(
                    text = dayName,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.colors.textMuted,
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Month Grid Rows
        for (r in 0 until rows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (c in 0 until 7) {
                    val cellIndex = r * 7 + c
                    val dayNum = cellIndex - firstDayOfWeek + 1

                    if (dayNum in 1..daysInMonth) {
                        val dateIso = "${month.year}-${month.month.toString().padStart(2, '0')}-${dayNum.toString().padStart(2, '0')}"
                        val isToday = dateIso == todayIso
                        val isSelected = dateIso == selectedDateIso
                        val episodes = episodesByDate[dateIso].orEmpty()

                        CalendarDayGridCell(
                            dayNumber = dayNum,
                            dateIso = dateIso,
                            isToday = isToday,
                            isSelected = isSelected,
                            episodes = episodes,
                            onClick = { onDateSelected(dateIso) },
                            onEpisodeClick = onEpisodeClick,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    } else {
                        // Empty cell outside current month
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.015f)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayGridCell(
    dayNumber: Int,
    dateIso: String,
    isToday: Boolean,
    isSelected: Boolean,
    episodes: List<CalendarEpisodeItem>,
    onClick: () -> Unit,
    onEpisodeClick: (type: String, id: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val borderColor = when {
        isSelected -> tokens.colors.accent
        isHovered -> Color.White.copy(alpha = 0.22f)
        isToday -> tokens.colors.accent.copy(alpha = 0.40f)
        else -> Color.White.copy(alpha = 0.05f)
    }

    val backgroundColor = when {
        isSelected -> tokens.colors.accent.copy(alpha = 0.12f)
        isHovered -> Color.White.copy(alpha = 0.07f)
        else -> Color(0xFF16161B).copy(alpha = 0.55f)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .hoverable(interactionSource)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Day number header (circular purple badge if today, like Stremio!)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isToday) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(tokens.colors.accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = dayNumber.toString(),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            ),
                        )
                    }
                } else {
                    Text(
                        text = dayNumber.toString(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) tokens.colors.textPrimary else tokens.colors.textSecondary,
                        ),
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                    )
                }

                if (episodes.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.10f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = "${episodes.size}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = tokens.colors.textPrimary,
                            ),
                        )
                    }
                }
            }

            // Episode Mini Posters Row - STRICTLY VERTICAL (Stremio style!)
            if (episodes.isNotEmpty()) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                ) {
                    val availableWidth = maxWidth
                    val posterWidth = 34.dp
                    val posterHeight = 51.dp // 2:3 ratio
                    val maxPosters = ((availableWidth.value) / 38).toInt().coerceIn(1, 4)
                    val showCount = if (episodes.size > maxPosters) (maxPosters - 1).coerceAtLeast(1) else episodes.size
                    val displayEpisodes = episodes.take(showCount)
                    val remaining = episodes.size - showCount

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.Start),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        displayEpisodes.forEach { ep ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color.Black.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                modifier = Modifier
                                    .width(posterWidth)
                                    .height(posterHeight)
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { onEpisodeClick(ep.showType, ep.showId) },
                            ) {
                                val posterUrl = ep.showPoster ?: ep.thumbnail
                                if (!posterUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = posterUrl,
                                        contentDescription = ep.showTitle,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                            }
                        }

                        if (remaining > 0) {
                            Box(
                                modifier = Modifier
                                    .width(22.dp)
                                    .height(posterHeight)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "+$remaining",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                    ),
                                )
                            }
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(1.dp))
            }
        }
    }
}

@Composable
private fun CalendarAgendaSidebar(
    month: CalendarMonth,
    todayIso: String,
    selectedDateIso: String?,
    episodesByDate: Map<String, List<CalendarEpisodeItem>>,
    allEpisodes: List<CalendarEpisodeItem>,
    onEpisodeClick: (type: String, id: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio

    val displayGroups: List<Pair<String, List<CalendarEpisodeItem>>> = remember(selectedDateIso, month, episodesByDate, allEpisodes) {
        if (!selectedDateIso.isNullOrBlank()) {
            val items = episodesByDate[selectedDateIso].orEmpty()
            if (items.isNotEmpty()) listOf(selectedDateIso to items) else emptyList()
        } else {
            val monthPrefix = "${month.year}-${month.month.toString().padStart(2, '0')}"
            episodesByDate
                .filterKeys { it.startsWith(monthPrefix) }
                .toList()
                .sortedBy { it.first }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Sidebar Title
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (!selectedDateIso.isNullOrBlank()) {
                    formatReleaseDateForDisplay(selectedDateIso)
                } else {
                    "Uscite del Mese"
                },
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = tokens.colors.textPrimary,
                ),
            )

            if (!selectedDateIso.isNullOrBlank()) {
                Text(
                    text = "Mostra tutti",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = tokens.colors.accent,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { CalendarRepository.selectDate(null) }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }

        if (displayGroups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (!selectedDateIso.isNullOrBlank()) {
                        "Nessun episodio in uscita in questa data"
                    } else {
                        "Nessuna uscita programmata per questo mese"
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = tokens.colors.textMuted,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                displayGroups.forEach { (dateIso, episodes) ->
                    item(key = "agenda_header_$dateIso") {
                        val isToday = dateIso == todayIso
                        val formattedDate = formatReleaseDateForDisplay(dateIso)
                        val headerLabel = if (isToday) "Oggi • $formattedDate" else formattedDate

                        Text(
                            text = headerLabel,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isToday) tokens.colors.accent else tokens.colors.textSecondary,
                            ),
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                        )
                    }

                    items(
                        items = episodes,
                        key = { "${it.showId}_${it.episodeId}_${it.releaseDateIso}" },
                    ) { episode ->
                        AgendaEpisodeItemRow(
                            episode = episode,
                            onClick = { onEpisodeClick(episode.showType, episode.showId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AgendaEpisodeItemRow(
    episode: CalendarEpisodeItem,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val surfaceColor = if (isHovered) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.03f)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = surfaceColor,
        border = BorderStroke(1.dp, if (isHovered) tokens.colors.accent.copy(alpha = 0.35f) else Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // STRICTLY VERTICAL POSTER (2:3 aspect ratio)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier
                    .width(52.dp)
                    .height(78.dp)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                val img = episode.showPoster ?: episode.thumbnail
                if (!img.isNullOrBlank()) {
                    AsyncImage(
                        model = img,
                        contentDescription = episode.showTitle,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }

            // Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = episode.showTitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
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
                            .clip(RoundedCornerShape(5.dp))
                            .background(tokens.colors.accent.copy(alpha = 0.18f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = code,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = tokens.colors.accent,
                            ),
                        )
                    }
                }

                if (!episode.episodeTitle.isNullOrBlank()) {
                    Text(
                        text = episode.episodeTitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = tokens.colors.textSecondary,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

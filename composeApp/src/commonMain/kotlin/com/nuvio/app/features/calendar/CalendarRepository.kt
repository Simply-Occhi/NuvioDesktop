package com.nuvio.app.features.calendar

import co.touchlab.kermit.Logger
import com.nuvio.app.core.format.formatReleaseDateForDisplay
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.core.time.daysUntilEpisodeRelease
import com.nuvio.app.core.time.parseEpisodeReleaseLocalDate
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.library.LibraryItem
import com.nuvio.app.features.library.LibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

object CalendarRepository {
    private val log = Logger.withTag("CalendarRepository")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var refreshJob: Job? = null

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private var cachedEpisodes: List<CalendarEpisodeItem> = emptyList()

    init {
        scope.launch {
            LibraryRepository.uiState
                .map { it.items }
                .distinctUntilChanged()
                .collectLatest {
                    refresh()
                }
        }
    }

    fun setFilter(filter: CalendarFilter) {
        _uiState.update { current ->
            current.copy(
                selectedFilter = filter,
                groups = buildGroups(cachedEpisodes, filter),
            )
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val libraryItems = LibraryRepository.uiState.value.items
                val series = libraryItems.filter { isSeries(it.type) }
                val concurrencySemaphore = Semaphore(4)

                val nowMs = EpisodeReleaseDatePlatform.nowEpochMs()
                val todayIso = EpisodeReleaseDatePlatform.localIsoDateAtEpochMs(nowMs) ?: ""

                val deferredEpisodes = series.map { show ->
                    async {
                        concurrencySemaphore.withPermit {
                            fetchShowEpisodes(show, todayIso)
                        }
                    }
                }

                val allEpisodes = deferredEpisodes.awaitAll().flatten()
                cachedEpisodes = allEpisodes

                val groups = buildGroups(allEpisodes, _uiState.value.selectedFilter)

                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        groups = groups,
                        totalEpisodesCount = allEpisodes.size,
                        trackedShowsCount = series.size,
                        errorMessage = null,
                    )
                }
            } catch (e: Exception) {
                log.w(e) { "Failed to refresh release calendar: ${e.message}" }
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    private suspend fun fetchShowEpisodes(
        show: LibraryItem,
        todayIso: String,
    ): List<CalendarEpisodeItem> {
        val meta = runCatching {
            MetaDetailsRepository.fetch(
                type = show.type,
                id = show.id,
                cacheResult = true,
            )
        }.getOrNull() ?: return emptyList()

        val showTitle = meta.name.ifBlank { show.name }
        val poster = show.poster ?: meta.poster

        return meta.videos.mapNotNull { video ->
            val releaseDate = parseEpisodeReleaseLocalDate(video.released) ?: return@mapNotNull null
            val daysDiff = if (todayIso.isNotBlank()) {
                daysUntilEpisodeRelease(todayIso, releaseDate)
            } else null

            // Show episodes from the last 7 days through all upcoming dates
            if (daysDiff != null && daysDiff < -7) {
                return@mapNotNull null
            }

            CalendarEpisodeItem(
                showId = show.id,
                showType = show.type,
                showTitle = showTitle,
                showPoster = poster,
                episodeId = video.id,
                seasonNumber = video.season,
                episodeNumber = video.episode,
                episodeTitle = video.title,
                releaseDateIso = releaseDate,
                daysUntilRelease = daysDiff,
                thumbnail = video.thumbnail ?: video.seasonPoster ?: meta.background ?: poster,
                overview = video.overview,
                posterShape = show.posterShape,
            )
        }
    }

    private fun buildGroups(
        episodes: List<CalendarEpisodeItem>,
        filter: CalendarFilter,
    ): List<CalendarDayGroup> {
        val filtered = when (filter) {
            CalendarFilter.ALL -> episodes
            CalendarFilter.DAYS_7 -> episodes.filter { (it.daysUntilRelease ?: Int.MAX_VALUE) in 0..7 }
            CalendarFilter.DAYS_30 -> episodes.filter { (it.daysUntilRelease ?: Int.MAX_VALUE) in 0..30 }
        }

        return filtered
            .sortedWith(
                compareBy<CalendarEpisodeItem> { it.releaseDateIso }
                    .thenBy { it.showTitle }
                    .thenBy { it.seasonNumber ?: 0 }
                    .thenBy { it.episodeNumber ?: 0 }
            )
            .groupBy { it.releaseDateIso }
            .map { (dateIso, itemsForDate) ->
                val daysDiff = itemsForDate.firstOrNull()?.daysUntilRelease
                val isToday = daysDiff == 0
                val isTomorrow = daysDiff == 1
                val isYesterday = daysDiff == -1

                val displayDate = formatReleaseDateForDisplay(dateIso)
                val label = when {
                    isToday -> "Oggi • $displayDate"
                    isTomorrow -> "Domani • $displayDate"
                    isYesterday -> "Ieri • $displayDate"
                    else -> displayDate
                }

                CalendarDayGroup(
                    dateIso = dateIso,
                    label = label,
                    isToday = isToday,
                    isTomorrow = isTomorrow,
                    episodes = itemsForDate,
                )
            }
    }

    private fun isSeries(type: String): Boolean = when (type.trim().lowercase()) {
        "tv", "show", "series", "tvshow" -> true
        else -> false
    }
}

package com.nuvio.app.features.calendar

import co.touchlab.kermit.Logger
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object CalendarRepository {
    private val log = Logger.withTag("CalendarRepository")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private var refreshJob: Job? = null

    private val initialMonth: CalendarMonth by lazy {
        val nowMs = EpisodeReleaseDatePlatform.nowEpochMs()
        val todayIso = EpisodeReleaseDatePlatform.localIsoDateAtEpochMs(nowMs) ?: "2026-11-01"
        val y = todayIso.substring(0, 4).toIntOrNull() ?: 2026
        val m = todayIso.substring(5, 7).toIntOrNull() ?: 11
        CalendarMonth(y, m)
    }

    private val _uiState = MutableStateFlow(CalendarUiState(currentMonth = initialMonth))
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private var cachedEpisodes: List<CalendarEpisodeItem> = emptyList()

    init {
        loadPersistedCache()

        scope.launch {
            LibraryRepository.uiState
                .filter { it.isLoaded }
                .map { it.items }
                .distinctUntilChanged()
                .collectLatest {
                    refresh()
                }
        }
    }

    fun selectMonth(month: CalendarMonth) {
        _uiState.update { it.copy(currentMonth = month) }
    }

    fun selectDate(dateIso: String?) {
        _uiState.update { it.copy(selectedDateIso = dateIso) }
    }

    private fun loadPersistedCache() {
        runCatching {
            val payload = CalendarStorage.loadPayload()
            if (!payload.isNullOrBlank()) {
                val list = json.decodeFromString<List<CalendarEpisodeItem>>(payload)
                if (list.isNotEmpty()) {
                    cachedEpisodes = list
                    val grouped = list.groupBy { it.releaseDateIso }
                    _uiState.update { current ->
                        current.copy(
                            episodesByDate = grouped,
                            isLoading = false,
                        )
                    }
                    log.d { "Loaded ${list.size} cached calendar episodes from disk" }
                }
            }
        }.onFailure {
            log.w(it) { "Could not load cached calendar payload: ${it.message}" }
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            val hasExistingData = cachedEpisodes.isNotEmpty()
            _uiState.update {
                it.copy(
                    isLoading = !hasExistingData,
                    isRefreshing = true,
                    errorMessage = null,
                )
            }

            try {
                LibraryRepository.ensureLoaded()
                val libraryState = LibraryRepository.uiState.value
                val libraryItems = libraryState.items
                val series = libraryItems.filter { isSeries(it.type) }
                val validSeriesIds = series.map { it.id }.toSet()

                if (series.isEmpty()) {
                    if (libraryState.isLoaded) {
                        cachedEpisodes = emptyList()
                        runCatching { CalendarStorage.savePayload("[]") }
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                episodesByDate = emptyMap(),
                                trackedShowsCount = 0,
                            )
                        }
                    } else {
                        _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
                    }
                    return@launch
                }

                val concurrencySemaphore = Semaphore(24)

                val nowMs = EpisodeReleaseDatePlatform.nowEpochMs()
                val todayIso = EpisodeReleaseDatePlatform.localIsoDateAtEpochMs(nowMs) ?: "2026-11-01"

                // Seed with cached episodes that still belong to currently tracked series
                val allEpisodesMap = cachedEpisodes
                    .filter { it.showId in validSeriesIds }
                    .associateBy { "${it.showId}_${it.episodeId}_${it.releaseDateIso}" }
                    .toMutableMap()

                // Process in chunks to stream progressive updates to UI
                val chunks = series.chunked(8)
                for (chunk in chunks) {
                    val chunkResults = chunk.map { show ->
                        async {
                            concurrencySemaphore.withPermit {
                                fetchShowEpisodes(show, todayIso)
                            }
                        }
                    }.awaitAll().flatten()

                    for (ep in chunkResults) {
                        allEpisodesMap["${ep.showId}_${ep.episodeId}_${ep.releaseDateIso}"] = ep
                    }

                    val currentEpisodes = allEpisodesMap.values.toList()
                    val currentGrouped = currentEpisodes.groupBy { it.releaseDateIso }

                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            episodesByDate = currentGrouped,
                            trackedShowsCount = series.size,
                        )
                    }
                }

                val finalEpisodes = allEpisodesMap.values.toList()
                cachedEpisodes = finalEpisodes
                val finalGrouped = finalEpisodes.groupBy { it.releaseDateIso }

                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        isRefreshing = false,
                        episodesByDate = finalGrouped,
                        trackedShowsCount = series.size,
                        errorMessage = null,
                    )
                }

                // Save latest to disk
                runCatching {
                    val serialized = json.encodeToString(finalEpisodes)
                    CalendarStorage.savePayload(serialized)
                }
            } catch (e: Exception) {
                log.w(e) { "Failed to refresh calendar: ${e.message}" }
                _uiState.update { it.copy(isLoading = false, isRefreshing = false, errorMessage = e.message) }
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
                cacheResult = false,
            )
        }.getOrNull() ?: return emptyList()

        val showTitle = meta.name.ifBlank { show.name }.intern()
        val showType = show.type.intern()
        val showId = show.id.intern()
        // Ensure vertical poster is prioritized and interned
        val poster = (meta.poster ?: show.poster ?: meta.videos.firstOrNull()?.seasonPoster)?.intern()

        return meta.videos.mapNotNull { video ->
            val releaseDate = parseEpisodeReleaseLocalDate(video.released) ?: return@mapNotNull null
            val daysDiff = if (todayIso.isNotBlank()) {
                daysUntilEpisodeRelease(todayIso, releaseDate)
            } else null

            // Drop past episodes older than 180 days (6 months) to minimize heap and state bloat
            if (daysDiff != null && daysDiff < -180) {
                return@mapNotNull null
            }

            CalendarEpisodeItem(
                showId = showId,
                showType = showType,
                showTitle = showTitle,
                showPoster = poster,
                episodeId = video.id,
                seasonNumber = video.season,
                episodeNumber = video.episode,
                episodeTitle = video.title,
                releaseDateIso = releaseDate.intern(),
                daysUntilRelease = daysDiff,
                thumbnail = if (poster == null) video.thumbnail ?: video.seasonPoster else null,
                overview = null,
                posterShape = show.posterShape,
            )
        }
    }

    private fun isSeries(type: String): Boolean = when (type.trim().lowercase()) {
        "tv", "show", "series", "tvshow", "anime" -> true
        else -> false
    }
}

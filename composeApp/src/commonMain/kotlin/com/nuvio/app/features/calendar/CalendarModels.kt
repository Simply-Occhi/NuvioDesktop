package com.nuvio.app.features.calendar

import com.nuvio.app.features.home.PosterShape

enum class CalendarFilter {
    ALL,
    DAYS_7,
    DAYS_30,
}

data class CalendarEpisodeItem(
    val showId: String,
    val showType: String = "series",
    val showTitle: String,
    val showPoster: String?,
    val episodeId: String,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val episodeTitle: String?,
    val releaseDateIso: String,
    val daysUntilRelease: Int?,
    val thumbnail: String?,
    val overview: String?,
    val posterShape: PosterShape = PosterShape.Poster,
)

data class CalendarDayGroup(
    val dateIso: String,
    val label: String,
    val isToday: Boolean,
    val isTomorrow: Boolean,
    val episodes: List<CalendarEpisodeItem>,
)

data class CalendarUiState(
    val isLoading: Boolean = false,
    val selectedFilter: CalendarFilter = CalendarFilter.ALL,
    val groups: List<CalendarDayGroup> = emptyList(),
    val totalEpisodesCount: Int = 0,
    val trackedShowsCount: Int = 0,
    val errorMessage: String? = null,
)

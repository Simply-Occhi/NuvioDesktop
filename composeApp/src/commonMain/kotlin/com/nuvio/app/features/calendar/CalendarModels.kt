package com.nuvio.app.features.calendar

import com.nuvio.app.features.home.PosterShape
import kotlinx.serialization.Serializable

@Serializable
data class CalendarEpisodeItem(
    val showId: String,
    val showType: String = "series",
    val showTitle: String,
    val showPoster: String? = null,
    val episodeId: String,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val releaseDateIso: String,
    val daysUntilRelease: Int? = null,
    val thumbnail: String? = null,
    val overview: String? = null,
    val posterShape: PosterShape = PosterShape.Poster,
)

data class CalendarMonth(
    val year: Int,
    val month: Int, // 1..12
)

data class CalendarDayCell(
    val dateIso: String,
    val dayNumber: Int,
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val isSelected: Boolean,
    val episodes: List<CalendarEpisodeItem>,
)

data class CalendarUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val currentMonth: CalendarMonth = CalendarDateUtils.todayMonth(),
    val selectedDateIso: String? = null,
    val allEpisodes: List<CalendarEpisodeItem> = emptyList(),
    val episodesByDate: Map<String, List<CalendarEpisodeItem>> = emptyMap(),
    val trackedShowsCount: Int = 0,
    val errorMessage: String? = null,
)

object CalendarDateUtils {
    fun daysInMonth(year: Int, month: Int): Int = when (month) {
        2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

    /**
     * Returns 0 for Monday, 1 for Tuesday, ..., 6 for Sunday.
     */
    fun dayOfWeek(year: Int, month: Int, day: Int): Int {
        val t = intArrayOf(0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4)
        var y = year
        if (month < 3) y -= 1
        val d = (y + y / 4 - y / 100 + y / 400 + t[month - 1] + day) % 7
        return (d + 6) % 7
    }

    fun getMonthName(month: Int): String = when (month) {
        1 -> "Gennaio"
        2 -> "Febbraio"
        3 -> "Marzo"
        4 -> "Aprile"
        5 -> "Maggio"
        6 -> "Giugno"
        7 -> "Luglio"
        8 -> "Agosto"
        9 -> "Settembre"
        10 -> "Ottobre"
        11 -> "Novembre"
        12 -> "Dicembre"
        else -> ""
    }

    fun getShortMonthName(month: Int): String = when (month) {
        1 -> "Gen"
        2 -> "Feb"
        3 -> "Mar"
        4 -> "Apr"
        5 -> "Mag"
        6 -> "Giu"
        7 -> "Lug"
        8 -> "Ago"
        9 -> "Set"
        10 -> "Ott"
        11 -> "Nov"
        12 -> "Dic"
        else -> ""
    }

    fun previousMonth(month: CalendarMonth): CalendarMonth {
        return if (month.month == 1) {
            CalendarMonth(month.year - 1, 12)
        } else {
            CalendarMonth(month.year, month.month - 1)
        }
    }

    fun nextMonth(month: CalendarMonth): CalendarMonth {
        return if (month.month == 12) {
            CalendarMonth(month.year + 1, 1)
        } else {
            CalendarMonth(month.year, month.month + 1)
        }
    }

    fun todayMonth(): CalendarMonth {
        // Fallback default
        return CalendarMonth(2026, 11)
    }
}

package com.nanobuo.mypodder.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nanobuo.mypodder.data.db.AppDatabase
import com.nanobuo.mypodder.data.db.DayTotal
import com.nanobuo.mypodder.data.db.PodcastTotal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import javax.inject.Inject

enum class StatsPeriod { DAY, WEEK, MONTH }

data class Bar(val label: String, val detail: String, val valueMs: Long)

data class StatsUi(
    val totalMs: Long = 0,
    val savedMs: Long = 0,
    val completed: Int = 0,
    val periodMs: Long = 0,
    val bars: List<Bar> = emptyList(),
    val top: List<PodcastTotal> = emptyList(),
    val streak: Int = 0,
    val bestStreak: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(db: AppDatabase) : ViewModel() {
    private val dao = db.listeningDao()

    private val _period = MutableStateFlow(StatsPeriod.DAY)
    val period = _period.asStateFlow()

    fun setPeriod(p: StatsPeriod) {
        _period.value = p
    }

    val ui: StateFlow<StatsUi?> = combine(
        dao.observeTotals(0),
        _period.flatMapLatest { p -> dao.observeDays(startOf(p).toEpochDay()) },
        _period.flatMapLatest { p -> dao.observeTopPodcasts(startOf(p).toEpochDay(), 5) },
        dao.observeActiveDays(MIN_ACTIVE_MS),
        _period,
    ) { totals, days, top, active, p ->
        val (streak, best) = streaks(active)
        StatsUi(
            totalMs = totals.listenedMs,
            savedMs = (totals.contentMs - totals.listenedMs).coerceAtLeast(0),
            completed = totals.completed,
            periodMs = days.sumOf { it.listenedMs },
            bars = bars(p, days),
            top = top,
            streak = streak,
            bestStreak = best,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun startOf(p: StatsPeriod): LocalDate {
        val today = LocalDate.now()
        return when (p) {
            StatsPeriod.DAY -> today.minusDays(DAYS - 1L)
            StatsPeriod.WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(WEEKS - 1L)
            StatsPeriod.MONTH -> YearMonth.now().minusMonths(MONTHS - 1L).atDay(1)
        }
    }

    private fun bars(p: StatsPeriod, days: List<DayTotal>): List<Bar> {
        val byDay = days.associate { it.day to it.listenedMs }
        val locale = Locale.getDefault()
        val start = startOf(p)
        val dateFormat = DateTimeFormatter.ofPattern("d MMM", locale)
        return when (p) {
            StatsPeriod.DAY -> (0 until DAYS).map { i ->
                val d = start.plusDays(i.toLong())
                Bar(
                    label = d.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                    detail = dateFormat.format(d),
                    valueMs = byDay[d.toEpochDay()] ?: 0,
                )
            }
            StatsPeriod.WEEK -> (0 until WEEKS).map { i ->
                val from = start.plusWeeks(i.toLong())
                val sum = (0L until 7L).sumOf { byDay[from.plusDays(it).toEpochDay()] ?: 0 }
                Bar(
                    label = from.dayOfMonth.toString(),
                    detail = dateFormat.format(from) + " – " + dateFormat.format(from.plusDays(6)),
                    valueMs = sum,
                )
            }
            StatsPeriod.MONTH -> (0 until MONTHS).map { i ->
                val month = YearMonth.from(start).plusMonths(i.toLong())
                val sum = (1..month.lengthOfMonth()).sumOf { byDay[month.atDay(it).toEpochDay()] ?: 0 }
                Bar(
                    label = month.month.getDisplayName(TextStyle.NARROW, locale),
                    detail = month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale) + " " + month.year,
                    valueMs = sum,
                )
            }
        }
    }

    /** Serie corrente (che termina oggi o ieri) e serie migliore di giorni consecutivi. */
    private fun streaks(activeDaysDesc: List<Long>): Pair<Int, Int> {
        if (activeDaysDesc.isEmpty()) return 0 to 0
        val today = LocalDate.now().toEpochDay()
        var current = 0
        if (activeDaysDesc.first() >= today - 1) {
            var expected = activeDaysDesc.first()
            for (d in activeDaysDesc) {
                if (d == expected) {
                    current++
                    expected--
                } else {
                    break
                }
            }
        }
        var best = 1
        var run = 1
        for (i in 1 until activeDaysDesc.size) {
            run = if (activeDaysDesc[i] == activeDaysDesc[i - 1] - 1) run + 1 else 1
            best = maxOf(best, run)
        }
        return current to best
    }

    private companion object {
        const val DAYS = 14
        const val WEEKS = 12
        const val MONTHS = 12
        const val MIN_ACTIVE_MS = 60_000L
    }
}

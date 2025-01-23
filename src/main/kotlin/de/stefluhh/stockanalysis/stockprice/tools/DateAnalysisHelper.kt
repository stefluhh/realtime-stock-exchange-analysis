package de.stefluhh.stockanalysis.stockprice.tools

import java.time.*
import java.time.temporal.TemporalAdjusters


object DateAnalysisHelper {

    private val TRADING_HOURS_FROM = LocalTime.of(15, 29)
    private val TRADING_HOURS_TO = LocalTime.of(22, 1)

    /**
     * Times are EXCLUSIVE.
     */
    fun isTimeBetween(date: Instant, fromTimeCET: LocalTime, toTimeCET: LocalTime): Boolean {
        // Get the time in the Europe/Berlin time zone
        val berlinZone = ZoneId.of("Europe/Berlin")
        val localDateTime = date.atZone(berlinZone)
        val localTime = localDateTime.toLocalTime()

        // Determine if it's daylight saving time (DST) in Berlin
        val isDST = berlinZone.rules.isDaylightSavings(localDateTime.toInstant())

        // Check if we are in the 1-week transition period where Germany has switched to winter time
        // but the US is still in summer time (last Sunday in October to first Sunday in November)
        val year = localDateTime.year
        val lastSundayInOctober = LocalDate.of(year, 10, 31).with(TemporalAdjusters.lastInMonth(DayOfWeek.SUNDAY))
        val firstSundayInNovember = LocalDate.of(year, 11, 1).with(TemporalAdjusters.firstInMonth(DayOfWeek.SUNDAY))

        val inTransitionWeek = localDateTime.toLocalDate().isAfter(lastSundayInOctober) &&
                localDateTime.toLocalDate().isBefore(firstSundayInNovember)

        // Set suppression time based on DST and transition week
        val allowedFrom = calculateTimes(isDST, fromTimeCET, inTransitionWeek)
        val allowedTo = calculateTimes(isDST, toTimeCET, inTransitionWeek)

        // Check if the current time falls within the suppression window
        return localTime.isAfter(allowedFrom) && localTime.isBefore(allowedTo)
    }

    private fun calculateTimes(
        isDST: Boolean,
        fromTimeCET: LocalTime,
        inTransitionWeek: Boolean
    ): LocalTime? = when {
        isDST -> fromTimeCET // Summer time in Germany
        inTransitionWeek -> fromTimeCET.minusHours(1) // Transition week
        else -> fromTimeCET.minusHours(2) // Winter time in Germany
    }

}
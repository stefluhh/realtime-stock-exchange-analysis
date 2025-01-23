package de.stefluhh.stockanalysis.stockprice

import de.stefluhh.stockanalysis.config.extension.toLocalDate
import de.stefluhh.stockanalysis.service.NavigableDateMap
import java.time.Instant
import java.time.LocalDate
import kotlin.math.abs

data class Gap(
    val absolute: Double,
    val percentage: Double,
    val time: Instant? = null
) {
    fun isAtLeast(minGapSize: Double): Boolean {
        return abs(percentage) >= minGapSize
    }

    fun isPositive(): Boolean {
        return percentage > 0.0
    }

    fun gotClosedAfter(
        gapData: Stockprice,
        prices: NavigableDateMap<LocalDate, Stockprice>,
        until: LocalDate
    ): Boolean {
        prices
            .tailMap(gapData.date.toLocalDate())
            .headMap(until)
            .forEach { (_, stockprice) ->
                if (stockprice.priceClose <= gapData.priceOpen) {
                    return true
                }
            }

        return false
    }

    companion object {

        val MIN_GAP_PERCENT = 0.05

        fun calculateGap(today: Stockprice, dayBefore: Stockprice): Gap? {
            if (dayBefore.priceClose.toDouble() <= 0.0) return null
            val absolute = today.priceOpen - dayBefore.priceClose
            val percentage = (today.priceOpen / dayBefore.priceClose).toDouble() - 1.0
            return if (abs(percentage) >= MIN_GAP_PERCENT) {
                Gap(absolute.toDouble(), percentage, today.date)
            } else null
        }
    }

}

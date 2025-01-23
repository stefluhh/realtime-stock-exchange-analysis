package de.stefluhh.stockpicker.stockprice.tools

import de.stefluhh.stockpicker.config.extension.rounded
import de.stefluhh.stockpicker.stockprice.MovingAverage
import de.stefluhh.stockpicker.stockprice.Stockprice
import kotlin.math.pow
import kotlin.math.sqrt

object MovingAverageCalculator {

    /**
     * Assumes the list is sorted by date in ascending order.
     */
    fun calculateSMAAndDeviation(
        stockPrices: List<Stockprice>,
        period: Int,
        volume: Boolean
    ): MovingAverage? {

        val recentPrices = stockPrices.takeLast(period)

        val averagePriceOrVolume =
            recentPrices.map { if (volume) it.volume.toDouble() else it.priceClose.toDouble() }.average()

        if (averagePriceOrVolume <= 0.0) {
            return null
        }

        val recentPriceOrValue = stockPrices.last().let { if (volume) it.volume.toDouble() else it.priceClose.toDouble() }
        val deviationFromCurrent = if (recentPriceOrValue == 0.0) 0.0 else (recentPriceOrValue / averagePriceOrVolume) - 1.0

        val variance = recentPrices.map { if (volume) it.volume.toDouble() else it.priceClose.toDouble() }
            .map { it - averagePriceOrVolume  }
            .map { (it.pow(2)) }
            .average()

        val standardDeviation = sqrt(variance)

        val coefficientOfVariation = standardDeviation / averagePriceOrVolume

        return MovingAverage(
            periods = period,
            value = averagePriceOrVolume.rounded(2),
            valueHR = BillionToHumanReadableString.convert(averagePriceOrVolume.toLong()),
            deviationFromCurrent = deviationFromCurrent.rounded(3),
            coefficientOfVariation = coefficientOfVariation.rounded(3)
        )
    }

}
package de.stefluhh.stockpicker.stockprice

import de.stefluhh.stockpicker.stockprice.tools.MedianCalculator.calculateIQR
import de.stefluhh.stockpicker.stockprice.tools.MovingAverageCalculator.calculateSMAAndDeviation
import org.bson.types.ObjectId
import java.math.BigDecimal
import java.time.Instant
import kotlin.math.max

typealias StockpriceId = ObjectId
typealias Symbol = String

data class Stockprice(
    val id: StockpriceId,
    val symbol: Symbol,
    val date: Instant = Instant.now(),
    val priceClose: BigDecimal,
    val priceOpen: BigDecimal,
    val priceLow: BigDecimal? = null,
    val priceHigh: BigDecimal? = null,
    val tradeCount: Long?,
    val biggestSingleTradeVolume: Long?,
    val volume: Long,
    val priceSmas: List<MovingAverage?> = emptyList(),
    val volumeSmas: List<MovingAverage?> = emptyList(),
    val volumeIqrs: List<IQR?> = emptyList(),
    val gap: Gap? = null,
    val aggregatedAt: Instant
) {

    fun calculateGapIfAny(previous: Stockprice?): Stockprice {
        if (previous == null) return this
        if (volume < 100_000 || previous.volume < 100_000) return this
        // Check if day difference is too big (more than 10 days)
        if (date.epochSecond - previous.date.epochSecond > 86400 * 10) {
            println("Too big day difference: ${(date.epochSecond - previous.date.epochSecond) / 86400} days on $symbol at $date")
            return this
        }

        return copy(gap = Gap.calculateGap(this, previous))
    }

    fun hasGap(): Boolean {
        return gap != null
    }

    /**
     * Must be sorted by date ascending
     */
    fun calculateMovingAverages(sortedPrices: List<Stockprice>): Stockprice {
        if (sortedPrices.isEmpty()) return this

        val priceSmas = listOf(
            calculateSMAAndDeviation(sortedPrices, 20, volume = false),
            calculateSMAAndDeviation(sortedPrices, 50, volume = false),
            calculateSMAAndDeviation(sortedPrices, 200, volume = false),
            calculateSMAAndDeviation(sortedPrices, 360, volume = false), // 6 Stunden bei 1-Minuten-Kerzen
        )

        val volumeSmas = listOf(
            calculateSMAAndDeviation(sortedPrices, 20, volume = true),
            calculateSMAAndDeviation(sortedPrices, 50, volume = true),
            calculateSMAAndDeviation(sortedPrices, 200, volume = true),
            calculateSMAAndDeviation(sortedPrices, 360, volume = true), // 6 Stunden bei 1-Minuten-Kerzen
        )

        return copy(priceSmas = priceSmas, volumeSmas = volumeSmas)
    }

    fun calculateIQRs(sortedPrices: List<Stockprice>): Stockprice {
        if (sortedPrices.isEmpty() || sortedPrices.size < 3) return this

        val volumeIqrs = listOf(
            calculateIQR(sortedPrices, 20, volume)?.accumulateHistoricOutliers(sortedPrices.takeLast(20).map { it.getVolumeIqr20() }),
            calculateIQR(sortedPrices, 50, volume)?.accumulateHistoricOutliers(sortedPrices.takeLast(50).map { it.getVolumeIqr50() }),
            calculateIQR(sortedPrices, 200, volume)?.accumulateHistoricOutliers(sortedPrices.takeLast(200).map { it.getVolumeIqr200() }),
        )

        return copy(volumeIqrs = volumeIqrs)
    }

    fun getVolumeSma360(): Double? {
        return volumeSmas.getOrNull(3)?.value
    }

    fun getVolumeSma200(): Double? {
        return volumeSmas.getOrNull(2)?.value
    }

    fun getVolumeSma50(): Double? {
        return volumeSmas.getOrNull(1)?.value
    }

    fun getVolumeIqr20(): IQR? {
        return volumeIqrs.getOrNull(0)
    }

    fun getVolumeIqr50(): IQR? {
        return volumeIqrs.getOrNull(1)
    }

    fun getVolumeIqr200(): IQR? {
        return volumeIqrs.getOrNull(2)
    }

    fun maxSmaLength(): Int {
        val maxPeriodPriceSma = priceSmas.mapNotNull { it?.periods }.maxOrNull() ?: 0
        val maxPeriodVolumeSma = volumeSmas.mapNotNull { it?.periods }.maxOrNull() ?: 0

        // If no sma was calculated yet, return 400 to suit most cases
        return max(400, maxOf(maxPeriodPriceSma, maxPeriodVolumeSma))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Stockprice

        if (symbol != other.symbol) return false
        if (date != other.date) return false

        return true
    }

    override fun hashCode(): Int {
        var result = symbol.hashCode()
        result = 31 * result + date.hashCode()
        return result
    }

    /**
     * Returns the amount of outliers in the last 50 ticks.
     * If no IQR was calculated yet, return 99 so that a strategy will stop analyzing if it takes IQR into consideration.
     * Otherwise, a false positive is likely to occur.
     */
    fun volumeOutlierCount50(): Int {
        return getVolumeIqr50()?.outlierCount ?: 99
    }

    fun volumeOutlierCount200(): Int {
        return getVolumeIqr200()?.outlierCount ?: 99
    }

    fun biggestSingleTradePercent(): Double {
        return biggestSingleTradeVolume?.toDouble()?.div(volume)?.times(100) ?: 0.0
    }


}

data class MovingAverage(
    val periods: Int,
    val value: Double,
    val valueHR: String,
    val deviationFromCurrent: Double = 0.0,
    val coefficientOfVariation: Double,
)

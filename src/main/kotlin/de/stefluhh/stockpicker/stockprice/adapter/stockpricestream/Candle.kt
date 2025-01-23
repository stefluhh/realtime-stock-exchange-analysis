package de.stefluhh.stockpicker.stockprice.adapter.stockpricestream

import de.stefluhh.stockpicker.stockprice.Stockprice
import java.time.Instant

data class Candle(
    val ticker: String,
    val openPrice: Double,
    val closePrice: Double,
    val highPrice: Double,
    val lowPrice: Double,
    val tradeCount: Long?,
    val biggestSingleTradeVolume: Long?,
    val exchangeBiggestTrade: String? = null,
    val volume: Long,
    val startTimestampMillis: Long,
    val endTimestampMillis: Long,
    val aggregatedAt: Long
) {
    /**
     * Returns the start time of this candle in format "HH:mm:ss"
     */
    fun startTime(): String {
        return Instant.ofEpochMilli(startTimestampMillis).toString().substring(11, 19)
    }

    /**
     * Returns the end time of this candle in format "HH:mm:ss"
     */
    fun endTime(): String {
        return Instant.ofEpochMilli(endTimestampMillis).toString().substring(11, 19)
    }

    fun isPopulated(): Boolean {
        return openPrice != 0.0 && closePrice != 0.0 && highPrice != 0.0 && lowPrice != 0.0
    }

    fun setFromLastKnownPrice(lastKnownPrice: Stockprice?) : Candle? {
        if (lastKnownPrice == null) {
            return null
        }

        val close = lastKnownPrice.priceClose.toDouble()
        return copy(
            openPrice = close,
            closePrice = close,
            highPrice = close,
            lowPrice = close
        )
    }


    companion object {
        val DUMMY = Candle("", 0.0, 0.0, 0.0, 0.0, 0, 0, null, 0, 0, 0, System.currentTimeMillis())
    }

}
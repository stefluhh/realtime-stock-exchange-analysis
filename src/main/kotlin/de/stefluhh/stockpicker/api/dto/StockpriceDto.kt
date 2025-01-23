package de.stefluhh.stockpicker.api.dto

import de.stefluhh.stockpicker.stockprice.Symbol
import java.time.Instant

data class StockpriceDto(
    val ticker: Symbol,
    val date: Instant,
    val volume: Double,
    val open: Double = 42.0,
    val high: Double = 42.0,
    val low: Double = 42.0,
    val close: Double = 42.0,
    val tradeCount: Int = 42,
    val biggestSingleTradeVolume: Long = 42,
    val trendlineShort: Double = 0.0,
    val trendlineMedium: Double = 0.0,
    val trendlineLong: Double = 0.0,
    val significantOutlierCount: Int = 0,
    val isOutlierToday: Boolean = false,
)
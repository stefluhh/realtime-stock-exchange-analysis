package de.stefluhh.stockpicker.stockprice.analysis.strategies

import de.stefluhh.stockpicker.stockprice.Stockprice
import de.stefluhh.stockpicker.stockprice.Ticker

data class AnalysisInput(
    val current : Stockprice,
    val before: Stockprice,
    val ticker: Ticker
) {
    fun currentTradeCount(): Long {
        return current.tradeCount ?: 0
    }
}
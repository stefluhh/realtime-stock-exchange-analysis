package de.stefluhh.stockanalysis.stockprice.analysis.strategies

import de.stefluhh.stockanalysis.stockprice.Stockprice
import de.stefluhh.stockanalysis.stockprice.Ticker

data class AnalysisInput(
    val current : Stockprice,
    val before: Stockprice,
    val ticker: Ticker
) {
    fun currentTradeCount(): Long {
        return current.tradeCount ?: 0
    }
}
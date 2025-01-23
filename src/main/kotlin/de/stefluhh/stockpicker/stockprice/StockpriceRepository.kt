package de.stefluhh.stockpicker.stockprice

import java.time.Instant

interface StockpriceRepository {

    fun insertAll(candles: List<Stockprice>)
    fun findLastNStockPrices(symbol: Symbol, amount: Int, skipLast: Boolean = false, beforeDate: Instant? = null): List<Stockprice>
}

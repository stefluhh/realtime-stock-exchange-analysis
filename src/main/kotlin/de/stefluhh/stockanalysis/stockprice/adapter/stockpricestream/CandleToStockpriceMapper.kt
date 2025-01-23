package de.stefluhh.stockanalysis.stockprice.adapter.stockpricestream

import de.stefluhh.stockanalysis.config.extension.toMoney
import de.stefluhh.stockanalysis.stockprice.Stockprice
import io.polygon.kotlin.sdk.websocket.PolygonWebSocketMessage.StocksMessage
import org.bson.types.ObjectId
import java.time.Instant

object CandleToStockpriceMapper {

    fun aggregateToCandle(aggregate: StocksMessage.Aggregate): Candle? {
        val ticker = aggregate.ticker ?: return null
        val openPrice = aggregate.openPrice ?: return null
        val closePrice = aggregate.closePrice ?: return null
        val highPrice = aggregate.highPrice ?: return null
        val lowPrice = aggregate.lowPrice ?: return null
        val volume = aggregate.volume?.toLong() ?: 0
        val startTimestampMillis = aggregate.startTimestampMillis ?: return null
        val endTimestampMillis = aggregate.endTimestampMillis ?: return null

        return Candle(
            ticker = ticker,
            openPrice = openPrice,
            closePrice = closePrice,
            highPrice = highPrice,
            lowPrice = lowPrice,
            tradeCount = null, // We don't know for aggregates
            biggestSingleTradeVolume = null, // We don't know for aggregates
            volume = volume,
            startTimestampMillis = startTimestampMillis,
            endTimestampMillis = endTimestampMillis,
            aggregatedAt = aggregate.startTimestampMillis!!
        )
    }

    fun toStockprice(candle: Candle): Stockprice {
        return Stockprice(
            id = ObjectId(),
            symbol = candle.ticker.uppercase(),
            date = Instant.ofEpochMilli(candle.startTimestampMillis),
            priceClose = candle.closePrice.toMoney(),
            priceOpen = candle.openPrice.toMoney(),
            priceHigh = candle.highPrice.toMoney(),
            priceLow = candle.lowPrice.toMoney(),
            tradeCount = candle.tradeCount,
            biggestSingleTradeVolume = candle.biggestSingleTradeVolume,
            volume = candle.volume,
            aggregatedAt = Instant.ofEpochMilli(candle.aggregatedAt)
        )
    }


}
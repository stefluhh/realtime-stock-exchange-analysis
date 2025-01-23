package de.stefluhh.stockpicker.stockprice.adapter.stockpricestream.trades

import de.stefluhh.stockpicker.config.extension.*
import de.stefluhh.stockpicker.stockprice.adapter.stockpricestream.Candle
import io.polygon.kotlin.sdk.websocket.PolygonWebSocketMessage.StocksMessage.Trade
import java.lang.Thread.startVirtualThread
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min

private val REGULAR_SALE_CONDITION = listOf(0)

class TradeToCandleAggregator(
    private val ticker: String,
    private val emitCallback: (Candle) -> Unit  // Callback mit Ticker){}
) {

    private val aggregatedTrades = ConcurrentHashMap<Long, AggregatedTrade>()
    private val lock = Any()
    private var lastTradeTimestamp: Long = System.currentTimeMillis()

    /**
     * Bis hierhin wurden alle Minuten abgeschlossen
     */
    private val acceptTradesNotEarlierThan = AtomicLong((System.currentTimeMillis() - 15 * 1000 * 60) / 60000)

    fun processTrade(trade: Trade) {
        val timestamp = trade.timestampMillis ?: return

        // Berechnung des Zeitfensters (Minute) basierend auf dem Zeitstempel des Trades
        val minuteKey = timestamp / 60000

        synchronized(lock) {
            if (minuteKey < acceptTradesNotEarlierThan.get()) {
                // Dieser MinuteKey wurde bereits entfernt, keine neuen Trades hinzufügen. Daten droppen.
                // Es ist besser die Daten zu droppen als sie auf die nächste Periode zu rollieren.
                return
            }

            val aggregation = aggregatedTrades.computeIfAbsent(minuteKey) { AggregatedTrade() }
            updateAggregation(aggregation, trade)  // Trades in den entsprechenden Minuten-Bucket einfügen
            lastTradeTimestamp = System.currentTimeMillis()
        }
    }

    fun emitAndCleanupOldAggregates() {
        synchronized(lock) {

            if (aggregatedTrades.size > 1) {

                val sortedMinuteKeys = aggregatedTrades.keys.sorted()
                val oldestMinuteKey = sortedMinuteKeys.first()

                acceptTradesNotEarlierThan.set(oldestMinuteKey)

                val aggregateToEmit = aggregatedTrades.remove(oldestMinuteKey)!!

                startVirtualThread { emitCallback(toCandle(aggregateToEmit, oldestMinuteKey)) }

            }
        }
    }

    private fun updateAggregation(aggregation: AggregatedTrade, trade: Trade) {
        val price = trade.price ?: return
        val size = trade.size ?: return

        var shouldUpdateHighLow = false
        var shouldUpdateLast = false
        var shouldUpdateVolume = false
        var isVolumeCorrection = false

        // Wenn keine Conditions vorhanden sind, nehmen wir an, es ist ein Regular Sale (Condition 0)
        val conditions = trade.conditions.ifEmpty { listOf(REGULAR_SALE_CONDITION) }

        for (conditionCode in conditions) {
            val conditionProps = conditionPropertiesMap[conditionCode]
            if (conditionProps != null) {
                if (conditionProps.updateHighLow) shouldUpdateHighLow = true
                if (conditionProps.updateLast) shouldUpdateLast = true
                if (conditionProps.updateVolume) shouldUpdateVolume = true
                if (conditionProps.isVolumeCorrection) isVolumeCorrection = true
            }
        }

        // Volumen korrigieren, falls es sich um eine Korrektur oder Stornierung handelt
        val longSize = size.toLong()

        if (isVolumeCorrection) {
            aggregation.totalVolume -= longSize
            aggregation.tradeCount--
        } else if (shouldUpdateVolume) {
            aggregation.totalVolume += longSize
            aggregation.tradeCount++
            if (longSize > aggregation.biggestTrade) {
                aggregation.exchangeBiggestTrade = trade.exchangeId?.toString()
                aggregation.biggestTrade = longSize
            }
        }

        if (aggregation.openPrice == null && shouldUpdateLast) {
            aggregation.openPrice = price
        }

        if (shouldUpdateLast) {
            aggregation.closePrice = price
        }

        if (shouldUpdateHighLow) {
            aggregation.highPrice = max(aggregation.highPrice, price)
            aggregation.lowPrice = min(aggregation.lowPrice, price)
        }
    }

    private fun toCandle(aggregatedTrade: AggregatedTrade, minuteKey: Long): Candle {
        val now = System.currentTimeMillis()
        return Candle(
            ticker = ticker,
            openPrice = aggregatedTrade.openPrice ?: 0.0,
            closePrice = aggregatedTrade.closePrice ?: 0.0,
            highPrice = aggregatedTrade.highPrice,
            lowPrice = aggregatedTrade.lowPrice,
            tradeCount = aggregatedTrade.tradeCount,
            volume = aggregatedTrade.totalVolume,
            biggestSingleTradeVolume = aggregatedTrade.biggestTrade,
            exchangeBiggestTrade = aggregatedTrade.exchangeBiggestTrade,
            startTimestampMillis = minuteKey * 60000,
            endTimestampMillis = (minuteKey + 1) * 60000,
            aggregatedAt = now
        )
    }

    companion object : WithLogger()

}

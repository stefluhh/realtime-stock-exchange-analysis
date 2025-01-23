package de.stefluhh.stockpicker.stockprice.adapter.stockpricestream.trades

import de.stefluhh.stockpicker.stockprice.adapter.stockpricestream.Candle
import io.polygon.kotlin.sdk.websocket.PolygonWebSocketMessage
import java.util.concurrent.ConcurrentHashMap

class TradeAggregator(
    private val emitCallback: (Candle) -> Unit  // Callback mit Ticker
) {
    private val tickerAggregators = ConcurrentHashMap<String, TradeToCandleAggregator>()

    fun processTrade(trade: PolygonWebSocketMessage.StocksMessage.Trade) {
        if (trade.size == null || trade.price == null || trade.ticker == null) return
        val ticker = trade.ticker ?: return
        val tickerAggregator =
            tickerAggregators.computeIfAbsent(ticker) { TradeToCandleAggregator(ticker, emitCallback) }
        tickerAggregator.processTrade(trade)
    }

    /**
     * Wir erstellen dafür den Minute Key von 10 Sekunden zuvor. Da der Scheduler zur Emitierung der Aggregationen immer
     * 2 Sekunden nach der vollen Minute startet, sind 10 Sekunden ein guter Wert, um sicherzustellen, dass wir den MinuteKey
     * der Vorminute treffen. Wir könnten auch 3 Sekunden verwenden, aber 10 Sekunden sind sicherer und es spielt keine Rolle.
     */
    fun emitAndCleanupOldAggregates() {
        val iterator = tickerAggregators.entries.iterator()
        while (iterator.hasNext()) {
            val (_, aggregator) = iterator.next()
            aggregator.emitAndCleanupOldAggregates()
        }
    }
}
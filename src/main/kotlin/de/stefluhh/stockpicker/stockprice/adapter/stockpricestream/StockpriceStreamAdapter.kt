package de.stefluhh.stockpicker.stockprice.adapter.stockpricestream

import de.stefluhh.stockpicker.config.extension.*
import de.stefluhh.stockpicker.stockprice.DailyIntervalStockpriceRepository
import de.stefluhh.stockpicker.stockprice.MinuteIntervalStockpriceRepository
import de.stefluhh.stockpicker.stockprice.ThirtyMinuteIntervalStockpriceRepository
import de.stefluhh.stockpicker.stockprice.adapter.stockpricestream.trades.TradeAggregator
import de.stefluhh.stockpicker.stockprice.analysis.AnalysisService
import de.stefluhh.stockpicker.stockprice.analysis.strategies.TimeIntervalType.MINUTELY
import de.stefluhh.stockpicker.stockprice.analysis.strategies.TimeIntervalType.THIRTY_MINUTES
import io.polygon.kotlin.sdk.websocket.*
import io.polygon.kotlin.sdk.websocket.PolygonWebSocketMessage.StocksMessage.Trade
import jakarta.annotation.PreDestroy
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.lang.Thread.sleep
import java.lang.Thread.startVirtualThread
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow

@Component
class StockpriceStreamAdapter(
    @Value("\${application.polygon-io.apiKey}") private val apiKey: String,
    @Value("\${application.stockprice-streaming.live}") private val enabled: Boolean,
    private val analysisService: AnalysisService,
    private val minuteIntervalStockpriceRepository: MinuteIntervalStockpriceRepository,
    private val dailyIntervalStockpriceRepository: DailyIntervalStockpriceRepository,
    private val thirtyMinuteIntervalStockpriceRepository: ThirtyMinuteIntervalStockpriceRepository
) {

    private val tradeAggregator = TradeAggregator(::aggregateReceived)

    private val thirtyMinuteCandleProcessor =
        DelayedCandleProcessor(analysisService, thirtyMinuteIntervalStockpriceRepository, timeIntervalType = THIRTY_MINUTES)
    private val oneMinuteCandleProcessor =
        DelayedCandleProcessor(analysisService, minuteIntervalStockpriceRepository, delay = 250, MINUTELY)

    private val exchangesWithoutDarkpools = listOf(1,3,9,10,11,2,12,17)
    private var websocketClient: PolygonWebSocketClient? = null
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 20
    private val reconnectDelay = 5000L // 5 Sekunden Delay für den Reconnect
    private val candleAggregator = CandleAggregator(::handle30MinuteCandle)
    private var streamingConfirmed = false
    private var lastMessageReceivedAt: Instant? = null
    private var isShuttingDown = false

    private var startedAt: Instant? = null

    /**
     * Starts streaming stockprices in realtime and starts the analysis loop.
     * In order to do so, we have to prewarm some caches before starting the streaming.
     * When the application has run a while, the caches can become quite big, so prewarming takes a while.
     */
    @EventListener(ApplicationReadyEvent::class)
    fun streamStockpricesAndStartAnalysisLoop() {
        if (!enabled) {
            LOGGER.i { "Streaming is disabled. Productive streaming won't be started. You may enable it by application config." }
            return
        }

        LOGGER.i { "Warming stockprice caches.."}
        minuteIntervalStockpriceRepository.warmCache()

        startVirtualThread(::connectAndStream)

        LOGGER.i { "Streaming stockprices..." }
        startedAt = Instant.now()

        val w1 = startVirtualThread { dailyIntervalStockpriceRepository.warmCache() }
        val w3 = startVirtualThread { thirtyMinuteIntervalStockpriceRepository.warmCache() }
        w1.join()
        w3.join()

        LOGGER.i { "All caches warmed up."}
    }
    private fun connectAndStream() {

        websocketClient = PolygonWebSocketClient(
            apiKey,
            Feed.Delayed,
            Market.Stocks,
            listener = object : PolygonWebSocketListener {

                override fun onAuthenticated(client: PolygonWebSocketClient) {
                    LOGGER.i { "onAuthenticated" }
                    reconnectAttempts = 0
                }

                override fun onReceive(
                    client: PolygonWebSocketClient,
                    message: PolygonWebSocketMessage
                ) {
                    try {
                        when (message) {
                            is Trade -> {
                                confirmStreamingIfNeeded(message)
                                if (!exchangesWithoutDarkpools.contains(message.exchangeId!!.toInt())) {
                                    return
                                }

                                tradeAggregator.processTrade(message)
                            }

                            else -> LOGGER.i { "Received Message: $message" }
                        }
                    } catch (e: Exception) {
                        LOGGER.e(e) { "Error while processing message. Continueing.." }
                    }
                }

                override fun onDisconnect(client: PolygonWebSocketClient) {
                    LOGGER.i { "onDisconnect" }
                    if (!isShuttingDown) {
                        LOGGER.i { "We are not shutting down!? Attempting to reconnect" }
                        scheduleReconnect("Disconnected for no apparent reason?") // Reconnect bei Disconnect
                    }
                }

                override fun onError(client: PolygonWebSocketClient, error: Throwable) {
                    LOGGER.e(error) { "onError.  Gracefully handling it. Reconnecting.." }
                    scheduleReconnect(error.message + error.stackTraceToString()) // Reconnect bei Fehler
                }

            })

        val subscriptions = listOf(
            PolygonWebSocketSubscription(PolygonWebSocketChannel.Stocks.Trades, "*"),
        )

        try {
            // We want to receive JSON
            websocketClient!!.sendRaw = false
            websocketClient!!.connectBlocking()
            sleep(200) // Wait for server to acknowledge connection
            websocketClient!!.subscribeBlocking(subscriptions)
            LOGGER.i { "Subscription successful!" }
        } catch (e: Exception) {
            LOGGER.e(e) { "Connection or subscription failed.  Gracefully handling it. Attempting to reconnect..." }
            scheduleReconnect(e.message + e.stackTraceToString())
        }

    }

    @Scheduled(cron = "2 * * * * *") // Jede 2. Sekunde nach der vollen Minute
    fun emitCandles() {
        if (!enabled || startedAt == null) return
        tradeAggregator.emitAndCleanupOldAggregates()

        if (map.values.any { it % 5 == 0 }) {
            LOGGER.i { "BIGGEST TRADES PER EXCHANGE: $map" }
        }
    }

    private fun confirmStreamingIfNeeded(message: PolygonWebSocketMessage) {
        if (!streamingConfirmed) {
            LOGGER.i { "Streaming confirmed! Message: $message" }
            streamingConfirmed = true
        }
    }

    private fun handle30MinuteCandle(candle: Candle) {
        thirtyMinuteCandleProcessor.onCandle(candle)
    }

    val map = ConcurrentHashMap<String, Int>()

    private fun aggregateReceived(candle: Candle) {

        val candleToUse = ensureCandleHasPrices(candle) ?: return

        if (candleToUse.volume > 1000) {
            val biggestTradePercentage = candleToUse.biggestSingleTradeVolume!! / candleToUse.volume * 100

            if (candleToUse.tradeCount!! > 10 && biggestTradePercentage > 20) {
                map.compute(candleToUse.exchangeBiggestTrade!!) { _, v -> v?.plus(1) ?: 1 }
            }
        }

        oneMinuteCandleProcessor.onCandle(candleToUse)
        candleAggregator.buffer(candleToUse)
        lastMessageReceivedAt = Instant.now()
    }

    private fun ensureCandleHasPrices(candle: Candle): Candle? {
        if (!candle.isPopulated()) {
            return candle.setFromLastKnownPrice(minuteIntervalStockpriceRepository.findLastNStockPrices(
                candle.ticker,
                1,
                false,
            ).firstOrNull())
        }
        return candle
    }

    // Planmäßige Überprüfung, ob der Stream funktioniert (Daten immitiert).
    // Erstmalig nach 5 Minuten, sonst alle 15 Minuten
    @Scheduled(initialDelay = 1000 * 60 * 2, fixedRate = 1000 * 60 * 15)
    fun checkStreamingStatus() {
        // Frühestens 3 Minuten nach Start des Streams, dann alle 15 Minuten
        if (!enabled || !has3MinutesPassedSinceStreamStarted()) return

        if (!StreamingHealthcheck.checkHealth(lastMessageReceivedAt)) {
            LOGGER.e { "Streaming is not healthy. Reconnecting..." }
            scheduleReconnect("Streaming is not healthy.")
        }
    }

    private fun has3MinutesPassedSinceStreamStarted(): Boolean {
        return startedAt != null && startedAt!!.plusSeconds(180).isBefore(Instant.now())
    }

    private fun scheduleReconnect(error: String) {
        if (reconnectAttempts < maxReconnectAttempts) {
            reconnectAttempts++
            LOGGER.i { "Reconnection attempt #$reconnectAttempts in $reconnectDelay ms" }
            sleep((reconnectDelay * (reconnectAttempts + 1.0).pow(2.0)).toLong()) // Beim 10. Versuch sind es 10 Minuten delay
            stopStreaming()
            connectAndStream()
        } else {
            LOGGER.e { "Max reconnect attempts reached. Giving up." }
            throw IllegalStateException("Max reconnect attempts reached. Error: $error")
        }
    }

    @PreDestroy
    fun stopStreaming() {
        LOGGER.i { "Spring shutdown signal received." }
        try {
            if (startedAt == null) {
                return
            }

            LOGGER.i { "Stop streaming..." }
            isShuttingDown = true
            websocketClient!!.disconnectBlocking()
            LOGGER.i { "Streaming stopped and all candle processing completed." }
        } catch (e: Exception) {
            LOGGER.e(e) { "Error during disconnect. Gracefully handling it." }
        }
    }

    companion object : WithLogger()

}

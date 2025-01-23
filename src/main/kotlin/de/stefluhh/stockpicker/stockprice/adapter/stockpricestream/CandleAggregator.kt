package de.stefluhh.stockpicker.stockprice.adapter.stockpricestream

import de.stefluhh.stockpicker.config.extension.WithLogger
import de.stefluhh.stockpicker.config.extension.e
import de.stefluhh.stockpicker.config.extension.t
import java.lang.Thread.startVirtualThread
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue

class CandleAggregator(
    private val onThirtyMinuteCandle: (Candle) -> Unit // Callback-Funktion
) {

    // Puffer für eingehende Aggregate (1-Minuten-Kerzen)
    private val candleQueue: BlockingQueue<Candle> = LinkedBlockingQueue()

    // Cache für 1-Minuten-Kerzen pro Ticker
    private val oneMinuteCandles = ConcurrentHashMap<String, MutableList<Candle>>()

    // Cache für 15-Minuten-Kerzen pro Ticker
    private val fifteenMinuteCandles = ConcurrentHashMap<String, MutableList<Candle>>()

    // Cache für 30-Minuten-Kerzen pro Ticker (zur Aggregation von 15-Minuten-Kerzen)
    private val thirtyMinuteCandles = ConcurrentHashMap<String, MutableList<Candle>>()

    // Flag zum Beenden des Worker-Threads
    @Volatile
    private var running = true

    // Initialisiere den Worker-Thread
    init {
        startWorkerThread()
    }

    // Methode zum Puffern eingehender Aggregate
    fun buffer(candle: Candle) {
//        LOGGER.t { "Buffering candle with startTime ${candle.startTime()} and endTime ${candle.endTime()}: $candle." }
        candleQueue.add(candle)
    }

    private fun startWorkerThread() {
        startVirtualThread {
            var processing = false
            while (running) {
                try {
                    val candle = candleQueue.take() // blocks until a candle is available
                    if (!processing) {
                        processing = true
                        LOGGER.t { "Processing new set of candles..." }
                        Thread.sleep(5) // Wait until all candles got emitted
                    }

                    if (candle != Candle.DUMMY) {
                        processCandle(candle)
                    }

                    if (candleQueue.isEmpty()) {
                        processing = false
                        LOGGER.t { "All candles processed!" }
                    }
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt() // Stelle sicher, dass der Thread korrekt beendet wird
                    LOGGER.e(e) { "Worker-Thread was interrupted! Fahre fort.." }
                } catch (e: Exception) {
                    LOGGER.e(e) { "Error occurred while processing Aggregate!" }
                }
            }
        }
    }

    // Methode zum Verarbeiten der Aggregate
    private fun processCandle(candle: Candle) {
        val ticker = candle.ticker

        // Füge die 1-Minuten-Kerze zum Cache hinzu
        val oneMinuteList = oneMinuteCandles.computeIfAbsent(ticker) { mutableListOf() }
        oneMinuteList.add(candle)

        // Berechne den Periodenstart für die 15-Minuten-Periode
        val fifteenMinutePeriodStart = getPeriodStart(candle.endTimestampMillis, 15)

        // Filtern Sie die 1-Minuten-Kerzen, die zu dieser 15-Minuten-Periode gehören
        val oneMinuteCandlesForPeriod = oneMinuteList.filter {
            getPeriodStart(it.endTimestampMillis, 15) == fifteenMinutePeriodStart
        }

        if (oneMinuteCandlesForPeriod.size >= 15 || periodHasEnded(candle.endTimestampMillis, 15)) {
            // Aggregate 1-Minuten-Kerzen zu einer 15-Minuten-Kerze
            val fifteenMinuteCandle = aggregateCandles(oneMinuteCandlesForPeriod)

            // Entferne verwendete 1-Minuten-Kerzen aus dem Cache
            oneMinuteList.removeAll(oneMinuteCandlesForPeriod)

            // Füge die 15-Minuten-Kerze zum Cache hinzu
            val fifteenMinuteList = fifteenMinuteCandles.computeIfAbsent(ticker) { mutableListOf() }
            fifteenMinuteList.add(fifteenMinuteCandle)

            // Prüfe, ob wir eine 30-Minuten-Kerze bilden können
            val thirtyMinutePeriodStart = getPeriodStart(fifteenMinuteCandle.endTimestampMillis, 30)
            val fifteenMinuteCandlesForPeriod = fifteenMinuteList.filter {
                getPeriodStart(it.endTimestampMillis, 30) == thirtyMinutePeriodStart
            }

            if (fifteenMinuteCandlesForPeriod.size == 2 || periodHasEnded(fifteenMinuteCandle.endTimestampMillis, 30)) {
                // Aggregate 15-Minuten-Kerzen zu einer 30-Minuten-Kerze
                val thirtyMinuteCandle = aggregateCandles(fifteenMinuteCandlesForPeriod)

                // Entferne verwendete 15-Minuten-Kerzen aus dem Cache
                fifteenMinuteList.removeAll(fifteenMinuteCandlesForPeriod)

                // Füge die 30-Minuten-Kerze zum Cache hinzu
                val thirtyMinuteList = thirtyMinuteCandles.computeIfAbsent(ticker) { mutableListOf() }
                thirtyMinuteList.add(thirtyMinuteCandle)

                // Emittiere die 30-Minuten-Kerze über den Callback
                onThirtyMinuteCandle(thirtyMinuteCandle)
            }
        }
    }

    private fun periodHasEnded(timeMillis: Long, periodMinutes: Int): Boolean {
        val currentTime = Instant.ofEpochMilli(timeMillis).atZone(ZoneId.of("UTC"))
        val periodEndTime = getPeriodStart(timeMillis, periodMinutes).plusMinutes(periodMinutes.toLong())
        return currentTime.isAfter(periodEndTime)
    }


    // Hilfsmethode zum Aggregieren von Kerzen
    private fun aggregateCandles(candles: List<Candle>): Candle {
        val aggregatedAt = System.currentTimeMillis()
        val ticker = candles.first().ticker
        val openPrice = candles.first().openPrice
        val closePrice = candles.last().closePrice
        val highPrice = candles.maxOf { it.highPrice }
        val lowPrice = candles.minOf { it.lowPrice }
        val tradeCount = candles.sumOf { it.tradeCount ?: 0 }
        val volume = candles.sumOf { it.volume }
        val startTimestampMillis = candles.first().startTimestampMillis
        val endTimestampMillis = candles.last().endTimestampMillis

        return Candle(
            ticker = ticker,
            openPrice = openPrice,
            closePrice = closePrice,
            highPrice = highPrice,
            lowPrice = lowPrice,
            tradeCount = tradeCount,
            volume = volume,
            biggestSingleTradeVolume = candles.maxOf { it.biggestSingleTradeVolume ?: 0 },
            startTimestampMillis = startTimestampMillis,
            endTimestampMillis = endTimestampMillis,
            aggregatedAt = aggregatedAt
        )
    }

    // Hilfsmethode zum Bestimmen des Periodenstartzeitpunkts
    private fun getPeriodStart(timeMillis: Long, periodMinutes: Int): ZonedDateTime {
        val zoneId = ZoneId.of("UTC")
        val time = Instant.ofEpochMilli(timeMillis).atZone(zoneId)
        val periodStartMinute = (time.minute / periodMinutes) * periodMinutes
        return time.truncatedTo(ChronoUnit.HOURS)
            .plusMinutes(periodStartMinute.toLong())
    }

    companion object : WithLogger()
}
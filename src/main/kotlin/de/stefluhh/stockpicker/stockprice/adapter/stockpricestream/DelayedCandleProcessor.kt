package de.stefluhh.stockpicker.stockprice.adapter.stockpricestream

import de.stefluhh.stockpicker.config.extension.WithLogger
import de.stefluhh.stockpicker.config.extension.d
import de.stefluhh.stockpicker.config.extension.e
import de.stefluhh.stockpicker.stockprice.Stockprice
import de.stefluhh.stockpicker.stockprice.StockpricePostProcessor
import de.stefluhh.stockpicker.stockprice.StockpriceRepository
import de.stefluhh.stockpicker.stockprice.analysis.AnalysisService
import de.stefluhh.stockpicker.stockprice.analysis.strategies.TimeIntervalType
import io.ktor.util.collections.*
import java.lang.Thread.startVirtualThread
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

open class DelayedCandleProcessor(
    protected val analysisService: AnalysisService,
    protected val stockpriceRepository: StockpriceRepository,
    protected val delay: Long = 5000,
    protected val timeIntervalType: TimeIntervalType
) {
    private val candleBuffer = Collections.synchronizedSet(LinkedHashSet<Stockprice>())
    private val executorService = Executors.newSingleThreadExecutor()
    private val isTimerRunning = AtomicBoolean(false)

    protected val stockpricePostProcessor = StockpricePostProcessor(stockpriceRepository)

    fun onCandle(candle: Candle) {
        this.onStockprice(CandleToStockpriceMapper.toStockprice(candle))
    }

    fun onStockprice(stockprice: Stockprice) {
        candleBuffer.add(stockprice)

        if (isTimerRunning.compareAndSet(false, true)) {
            startStopwatch()
        }
    }

    private fun startStopwatch() {
        LOGGER.d { "Stopwatch started, processing batch of candles in $delay ms..." }

        executorService.submit {
            try {
                Thread.sleep(delay) // warten
                processBufferedCandles()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (e: Exception) {
                LOGGER.e(e) { "Error during processing of candles." }
            } finally {
                isTimerRunning.set(false)
            }
        }
    }

    private fun processBufferedCandles() {
        if (candleBuffer.isEmpty()) {
            LOGGER.d { "No candles to process." }
            return
        }

        val batchToProcess = mutableListOf<Stockprice>()
        val iterator = candleBuffer.iterator()
        while (iterator.hasNext()) {
            batchToProcess.add(iterator.next())
            iterator.remove()
        }

        processBatch(batchToProcess)
    }

    open fun processBatch(stockprices: List<Stockprice>) {
        if (stockprices.isEmpty()) return

        try {
            val start = System.currentTimeMillis()
            val postProcessed = stockpricePostProcessor.process(stockprices)
            val end = System.currentTimeMillis()
            LOGGER.d { "Postprocessing took ${end - start} ms." }

            stockpriceRepository.insertAll(postProcessed)
            LOGGER.d { "Inserted all prices in ${System.currentTimeMillis() - end}ms" }

            startVirtualThread {
                analysisService.analyze(stockpriceRepository, postProcessed, timeIntervalType)
            }
        } catch (e: Exception) {
            LOGGER.e(e) { "Error during processing of candles." }
        }

    }

    companion object : WithLogger()
}
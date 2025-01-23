package de.stefluhh.stockpicker.stockprice.analysis

import de.stefluhh.stockpicker.config.extension.WithLogger
import de.stefluhh.stockpicker.config.extension.d
import de.stefluhh.stockpicker.config.extension.i
import de.stefluhh.stockpicker.config.extension.parallelFlatMap
import de.stefluhh.stockpicker.service.notification.AnalysisNotificationService
import de.stefluhh.stockpicker.stockprice.Stockprice
import de.stefluhh.stockpicker.stockprice.StockpriceRepository
import de.stefluhh.stockpicker.stockprice.Ticker
import de.stefluhh.stockpicker.stockprice.TickerRepository
import de.stefluhh.stockpicker.stockprice.analysis.strategies.*
import de.stefluhh.stockpicker.stockprice.analysis.strategies.TimeIntervalType.*
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.lang.Thread.sleep
import java.lang.Thread.startVirtualThread
import java.util.concurrent.atomic.AtomicInteger


@Service
class AnalysisService(
    private val tickerRepository: TickerRepository,
    private val analysisRepository: AnalysisRepository,
    private val notificationService: AnalysisNotificationService,
    @Value("\${application.analysis.enabled}") private val enabled: Boolean,
    @Value("\${application.stockprice-streaming.live}") private val runParallel: Boolean
) {

    private val runningAnalysis = AtomicInteger(0)
    private val minutelyAnalysisStrategyRegistry = listOf(VolumeAnalysisStrategy(MINUTELY))
    private val thirtyMinuteAnalysisStrategyRegistry = listOf(VolumeAnalysisStrategy(THIRTY_MINUTES))
    private val dailyAnalysisStrategyRegistry = listOf(VolumeAnalysisStrategy(DAILY))

    fun analyze(
        stockpriceRepository: StockpriceRepository,
        stockprices: List<Stockprice>,
        timeIntervalType: TimeIntervalType
    ) = runBlocking {
        if (!enabled) {
            LOGGER.d { "Analysis is disabled."}
            return@runBlocking
        }

        runningAnalysis.incrementAndGet()

        LOGGER.d { "Running analysis strategies using $timeIntervalType time interval type."}
        val start = System.currentTimeMillis()

        try {
            val byTicker = stockpricesByTickerNotNull(stockprices)

            val analysisResults = byTicker.entries.parallelFlatMap { entry ->

                val ticker = entry.key
                var prices = entry.value
                if (prices.size > 1) {
                    prices = prices.sortedBy { it.date }
                }

                prices.flatMap { current ->
                    val before = stockpriceRepository.findLastNStockPrices(ticker.id, 1, true).firstOrNull() ?: return@parallelFlatMap emptyList()
                    val analysisInput = AnalysisInput(
                        current = current,
                        before = before,
                        ticker = ticker
                    )

                    val results = when (timeIntervalType) {
                        MINUTELY -> minutelyAnalysisStrategyRegistry.mapNotNull { it.analyze(analysisInput) }
                        THIRTY_MINUTES -> thirtyMinuteAnalysisStrategyRegistry.mapNotNull { it.analyze(analysisInput) }
                        DAILY -> dailyAnalysisStrategyRegistry.mapNotNull { it.analyze(analysisInput) }
                    }

                    results
                }
            }

            analysisRepository.insertAll(analysisResults)

            if (analysisResults.isNotEmpty()) {
                LOGGER.d { "Notifying subscribers..." }
                startVirtualThread { notificationService.notify(analysisResults) }
            }

            LOGGER.i { "All analysis strategies ran successfully in ${System.currentTimeMillis() - start}ms. Got ${analysisResults.size} signals." }
        } finally {
            runningAnalysis.decrementAndGet()
        }
    }

    private fun stockpricesByTickerNotNull(stockprices: List<Stockprice>): Map<Ticker, List<Stockprice>> {
        return stockprices.groupBy { tickerRepository.findById(it.symbol) }
            .mapNotNull { if (it.key == null) null else it.key!! to it.value }.toMap()
    }

    @PreDestroy
    fun onShutdown() {
        LOGGER.i { "Spring is shutting down. Waiting for analysis to finish..."}
        while (runningAnalysis.get() > 0) {
            try {
                sleep(500)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        LOGGER.i { "Analysis finished. Spring can shutdown now."}
    }

    companion object : WithLogger()

}

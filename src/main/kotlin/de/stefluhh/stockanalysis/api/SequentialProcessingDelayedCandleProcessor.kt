package de.stefluhh.stockanalysis.api

import de.stefluhh.stockanalysis.config.extension.WithLogger
import de.stefluhh.stockanalysis.config.extension.e
import de.stefluhh.stockanalysis.stockprice.Stockprice
import de.stefluhh.stockanalysis.stockprice.StockpriceRepository
import de.stefluhh.stockanalysis.stockprice.adapter.stockpricestream.DelayedCandleProcessor
import de.stefluhh.stockanalysis.stockprice.analysis.AnalysisService
import de.stefluhh.stockanalysis.stockprice.analysis.strategies.TimeIntervalType
import java.lang.Thread.startVirtualThread

class SequentialProcessingDelayedCandleProcessor(
    analysisService: AnalysisService,
    stockpriceRepository: StockpriceRepository,
    delay: Long = 5000,
    timeIntervalType: TimeIntervalType
) : DelayedCandleProcessor(analysisService, stockpriceRepository, delay, timeIntervalType) {

    override fun processBatch(stockprices: List<Stockprice>) {
        if (stockprices.isEmpty()) return

        stockprices.forEach { price ->
            try {
                val postProcessed = stockpricePostProcessor.process(listOf(price))
                stockpriceRepository.insertAll(postProcessed)
                startVirtualThread {
                    analysisService.analyze(stockpriceRepository, postProcessed, timeIntervalType)
                }
            } catch (e: Exception) {
                LOGGER.e(e) { "Error during processing of candles." }
            }
        }
    }

    companion object : WithLogger()

}
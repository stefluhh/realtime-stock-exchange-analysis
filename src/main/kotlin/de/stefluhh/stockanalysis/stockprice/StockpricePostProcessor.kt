package de.stefluhh.stockanalysis.stockprice

import de.stefluhh.stockanalysis.config.extension.WithLogger
import de.stefluhh.stockanalysis.config.extension.e
import de.stefluhh.stockanalysis.config.extension.t

/**
 * Not a Spring Bean.
 */
class StockpricePostProcessor(private val stockpriceRepository: StockpriceRepository) {

    fun process(stockprices: List<Stockprice>, recalculation: Boolean = false): List<Stockprice> {
        LOGGER.t { "Postprocessing ${stockprices.size} stockprices" }
        return stockprices.parallelStream().map {
            try {
                val lastPrices = stockpriceRepository.findLastNStockPrices(it.symbol, it.maxSmaLength(), skipLast = false, beforeDate = if (recalculation) it.date else null)

                it.calculateGapIfAny(lastPrices.lastOrNull()).calculateMovingAverages(lastPrices).calculateIQRs(lastPrices)
            } catch (e: Exception) {
                LOGGER.e(e) { "Error while processing stockprice" }
                null
            }

        }.toList().filterNotNull()
    }

    companion object : WithLogger()

}
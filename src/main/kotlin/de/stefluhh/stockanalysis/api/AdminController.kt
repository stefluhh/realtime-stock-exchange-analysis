package de.stefluhh.stockanalysis.api

import de.stefluhh.stockanalysis.api.dto.InsertPricesDto
import de.stefluhh.stockanalysis.api.dto.StockpriceDto
import de.stefluhh.stockanalysis.api.dto.StockpriceMapper
import de.stefluhh.stockanalysis.config.extension.WithLogger
import de.stefluhh.stockanalysis.config.extension.d
import de.stefluhh.stockanalysis.config.extension.i
import de.stefluhh.stockanalysis.stockprice.*
import de.stefluhh.stockanalysis.stockprice.analysis.AnalysisService
import de.stefluhh.stockanalysis.stockprice.analysis.strategies.TimeIntervalType.MINUTELY
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant

@RestController
class AdminController(
    private val minuteIntervalStockpriceRepository: MinuteIntervalStockpriceRepository,
    private val analysisService: AnalysisService,
    private val tickerRepository: TickerRepository,
) {

    private val oneMinuteCandleProcessor =
        SequentialProcessingDelayedCandleProcessor(analysisService, minuteIntervalStockpriceRepository, delay = 250, MINUTELY)

    @CrossOrigin(origins = ["http://localhost:63342"])
    @GetMapping("/admin/latest-stockprices")
    fun getStockprice(
        @RequestParam symbol: String,
        @RequestParam ticks: Int = 120
    ): ResponseEntity<List<StockpriceDto>> {
        LOGGER.i { "Fetching stockprices for $symbol" }

        minuteIntervalStockpriceRepository.warmCache()

        val stockprices = minuteIntervalStockpriceRepository
            .findLastNStockPrices(
                symbol = symbol.uppercase(),
                amount = ticks,
                skipLast = false,
            )
            .sortedBy { it.date }
            .map {
            StockpriceDto(
                ticker = it.symbol,
                date = it.date,
                open = it.priceOpen.toDouble(),
                high = it.priceHigh!!.toDouble(),
                low = it.priceLow!!.toDouble(),
                close = it.priceClose.toDouble(),
                volume = it.volume.toDouble(),
                trendlineShort = it.getVolumeIqr20()?.iqr ?: 0.0,
                trendlineMedium = it.getVolumeIqr50()?.iqr ?: 0.0,
                trendlineLong = it.getVolumeIqr200()?.iqr ?: 0.0,
                significantOutlierCount = it.getVolumeIqr50()?.outlierCount ?: 0,
                isOutlierToday = it.getVolumeIqr50()?.isSignificantOutlier() ?: false
            )
        }


        return ResponseEntity.ok(stockprices)
    }

    @PostMapping("/admin/stockprices/create-backgroundnoise")
    fun createBackgroundNoise(
        @RequestParam symbol: String,
        @RequestParam from: Instant,
        @RequestParam to: Instant,
        @RequestParam fuzzyness: RandomStockpriceFuzzyness
    ) : ResponseEntity<Unit> {
        LOGGER.i { "Creating background noise for $symbol from $from to $to" }

        Thread.sleep(1000)

        tickerRepository.save(TickerMockProvider.mockTicker(symbol))

        RandomStockpriceGenerator.generateRandomPrices(symbol, from, to, fuzzyness)
            .parallelStream()
            .forEach(oneMinuteCandleProcessor::onStockprice)


        return ResponseEntity.ok().build()
    }

    @PostMapping("/admin/stockprices/insert-stockprices")
    fun insertStockprice(
        @RequestBody data: InsertPricesDto
    ) : ResponseEntity<Unit> {
        LOGGER.i { "Inserting stockprices $data" }

        StockpriceMapper.toStockprices(data.data)
            .forEach(oneMinuteCandleProcessor::onStockprice)

        return ResponseEntity.ok().build()
    }


    @DeleteMapping("/admin/stockprices/{symbol}")
    fun deleteStockprices(
        @PathVariable symbol: String
    ) : ResponseEntity<Unit> {
        LOGGER.i { "Deleting stockprices for $symbol" }

        minuteIntervalStockpriceRepository.deleteAll(symbol)
        tickerRepository.delete(symbol)

        return ResponseEntity.ok().build()
    }

    @PostMapping("/admin/stockprices/recalculate")
    fun recalculateStockprices(
        @RequestParam symbol: String?
    ) {
        LOGGER.i { "Recalculating stockprices" }
        minuteIntervalStockpriceRepository.warmCache(symbol)
        LOGGER.d { "Cache warmed"}
        minuteIntervalStockpriceRepository.clearCalculations(symbol)
        LOGGER.d { "Calculations cleared" }

        val stockprices = minuteIntervalStockpriceRepository.findAll(symbol)
        stockprices.entries.parallelStream().forEach {
            val prices = it.value.sortedBy(Stockprice::date)
            prices.forEach { price ->
                minuteIntervalStockpriceRepository.save(StockpricePostProcessor(stockpriceRepository = minuteIntervalStockpriceRepository)
                    .process(listOf(price), recalculation = true).firstOrNull())
            }
            LOGGER.i { "Recalculated ${it.key}" }
        }

        LOGGER.i { "Recalculation finished" }
    }

    companion object : WithLogger()

}
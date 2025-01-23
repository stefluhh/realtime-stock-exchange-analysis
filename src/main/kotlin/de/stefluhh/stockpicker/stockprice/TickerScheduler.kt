package de.stefluhh.stockpicker.stockprice

import de.stefluhh.stockpicker.config.extension.WithLogger
import de.stefluhh.stockpicker.config.extension.i
import de.stefluhh.stockpicker.stockprice.adapter.ticker.StockTickerAdapter
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicLong

@Service
class TickerScheduler(
    private val stockTickerAdapter: StockTickerAdapter,
    private val tickerRepository: TickerRepository,
) {

    /**
     * Refreshes the list of all available tickers every day at 10:00
     */
    @Scheduled(cron = "0 0 10 * * *")
    fun refreshTickers() {
        LOGGER.i { "Refreshing tickers" }
        val count = AtomicLong(0)
        val newOnes = mutableListOf<Ticker>()
        val delistedOnes = mutableListOf<Ticker>()
        importTickers(newOnes, count, historicals = false, activeOnly = true) // Import active ones
        LOGGER.i { "Fetched active tickers." }

        importTickers(delistedOnes, count, historicals = false, activeOnly = false) // Import inactive (delisted) ones
        LOGGER.i { "Fetched inactive tickers." }

        LOGGER.i { "Tickers refreshed" }

        if (newOnes.isNotEmpty()) {
            LOGGER.i { "New tickers: ${newOnes.size}. ${newOnes.joinToString { it.id }}" }
        }

        if (delistedOnes.isNotEmpty()) {
            LOGGER.i { "Newly delisted tickers: ${delistedOnes.size}. ${delistedOnes.joinToString { it.id }}" }
        }

        tickerRepository.fetchAllActiveTickers() // Warm cache
        LOGGER.i { "Ticker symbols refreshed." }
    }

    /**
     * Refreshes the details of all tickers every day at 10:10
     */
    @Scheduled(cron = "0 10 10 * * *")
//    @EventListener(ApplicationReadyEvent::class)
    fun refreshTickerDetails() {
        Thread.startVirtualThread {
            LOGGER.i { "Refreshing ticker details" }

            val count = AtomicLong(0)

            tickerRepository.fetchAllActiveTickers().parallelStream().map {
                fetchDetailsForTicker(it)
            }.forEach { ticker ->
                tickerRepository.save(ticker)
                if (count.incrementAndGet() % 250 == 0L) LOGGER.i { "Saved tickers: ${count.get()}" }
            }

            LOGGER.i { "Ticker details refreshed" }
        }
    }

    private fun fetchDetailsForTicker(it: Ticker): Ticker {
        Thread.sleep(250) // Throttle requests
        return it.withDetails(stockTickerAdapter.fetchTickerDetails(it.id))
    }

    private fun importTickers(
        newTickers: MutableList<Ticker>,
        count: AtomicLong,
        historicals: Boolean,
        activeOnly: Boolean,
    ) {
        stockTickerAdapter.fetchAllTickers(historical = historicals, activeOnly = activeOnly).forEach {
            if (!tickerRepository.existsById(it.id)) {
                newTickers.add(it)
            }

            val existing = tickerRepository.findById(it.id)
            tickerRepository.save(it.withDetails(existing?.tickerDetails))
            if (count.incrementAndGet() % 250 == 0L) LOGGER.i { "Saved tickers: ${count.get()}" }
        }
    }

    companion object : WithLogger()

}
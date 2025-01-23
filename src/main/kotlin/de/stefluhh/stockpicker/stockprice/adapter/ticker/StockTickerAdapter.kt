package de.stefluhh.stockpicker.stockprice.adapter.ticker

import de.stefluhh.stockpicker.config.extension.WithLogger
import de.stefluhh.stockpicker.config.extension.e
import de.stefluhh.stockpicker.config.extension.i
import de.stefluhh.stockpicker.stockprice.Ticker
import de.stefluhh.stockpicker.stockprice.TickerDetails
import io.polygon.kotlin.sdk.rest.PolygonRestClient
import io.polygon.kotlin.sdk.rest.reference.SupportedTickersParameters
import io.polygon.kotlin.sdk.rest.reference.TickerDetailsParameters
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate

@Component
class StockTickerAdapter(
    private val apiClient: PolygonRestClient
) {

    fun fetchAllTickers(historical: Boolean = false, activeOnly: Boolean = true): List<Ticker> {
        LOGGER.i { "Fetching all tickers" }

        var count = 0

        val result = mutableListOf<Ticker>()
        val response = apiClient.referenceClient.listSupportedTickers(
            SupportedTickersParameters(
                date = if (historical) LocalDate.of(2019, 11, 1).toString() else LocalDate.now().toString(),
                sortBy = "ticker",
                type = "CS", // Common Stock
                market = "stocks",
                limit = 500,
                activeSymbolsOnly = activeOnly
            )
        )

        while (response.hasNext()) {
            val it = response.next()
            Ticker(
                id = it.ticker!!,
                currency = it.currencyName!!,
                market = it.market!!,
                name = it.name!!,
                type = it.type!!,
                primaryExchange = it.primaryExchange,
                active = it.active,
                lastUpdatedUtc = Instant.parse(it.lastUpdatedUtc!!),
                delistedUtc = it.delistedUtc?.let(Instant::parse),
            ).also { result.add(it) }

            if (++count % 250 == 0) LOGGER.i { "Fetched tickers: $count." }
        }

        LOGGER.i { "Fetched all tickers" }

        return result
    }

    fun fetchTickerDetails(id: String, retry: Boolean = false): TickerDetails? {

        try {
            return apiClient.referenceClient.getTickerDetailsV3Blocking(
                ticker = id,
                params = TickerDetailsParameters(
                    date = null // Most recent is default
                )
            ).let {
                val dto = it.results!!
                TickerDetails(
                    description = dto.description,
                    industry = dto.sicDescription,
                    homepageUrl = dto.homepageUrl,
                    totalEmployees = dto.totalEmployees,
                    iconUrl = dto.branding?.logoUrl,
                    listDate = dto.listDate?.let(LocalDate::parse),
                    marketCap = dto.marketCap?.toLong(),
                    shareClassSharesOutstanding = dto.shareClassSharesOutstanding,
                    weightedSharesOutstanding = dto.weightedSharesOutstanding?.toLong(),
                )
            }
        } catch (e: Exception) {
            Thread.sleep(500) // Throttle requests
            if (retry) {
                LOGGER.e { "Failed to fetch ticker details for $id. ${e.message}" }
                return null
            }
            // Retry once
            return fetchTickerDetails(id, true)
        }
    }

    companion object : WithLogger()
}

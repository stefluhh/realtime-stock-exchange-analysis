package de.stefluhh.stockpicker.api

import de.stefluhh.stockpicker.config.extension.toLocalDate
import de.stefluhh.stockpicker.stockprice.Ticker
import de.stefluhh.stockpicker.stockprice.TickerDetails
import java.time.Instant

object TickerMockProvider {
    fun mockTicker(symbol: String): Ticker {
        return Ticker(
            id = symbol,
            name = "Mock Ticker",
            currency = "USD",
            market = "US",
            type = "Stock",
            primaryExchange = "XNAS",
            active = true,
            lastUpdatedUtc = Instant.now(),
            delistedUtc = null,
            tickerDetails = mockTickerDetails()
        )
    }

    private fun mockTickerDetails(): TickerDetails {
        return TickerDetails(
            description = "Mock Description",
            industry = "Mock Industry",
            homepageUrl = "https://example.com",
            totalEmployees = 42,
            iconUrl = "https://example.com/icon.png",
            listDate = Instant.now().toLocalDate(),
            marketCap = 100_000_000,
            shareClassSharesOutstanding = 100_000_000,
            weightedSharesOutstanding = 100_000_000
        )
    }

}

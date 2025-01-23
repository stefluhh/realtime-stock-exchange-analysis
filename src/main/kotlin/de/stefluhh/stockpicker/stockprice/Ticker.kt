package de.stefluhh.stockpicker.stockprice

import de.stefluhh.stockpicker.config.extension.WithLogger
import de.stefluhh.stockpicker.config.extension.e
import de.stefluhh.stockpicker.stockprice.tools.BillionToHumanReadableString
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

data class Ticker(
    val id: String,
    val name: String,
    val currency: String,
    val market: String,
    val type: String,
    val primaryExchange: String?,
    val active: Boolean,
    val lastUpdatedUtc: Instant,
    val delistedUtc: Instant?,
    val tickerDetails: TickerDetails? = null
) {
    fun withDetails(details: TickerDetails?): Ticker {
        if (details?.marketCap == null && this.tickerDetails?.marketCap != null) return validationError("marketCap is null")

        return copy(tickerDetails = details)
    }

    private fun validationError(error: String): Ticker {
        LOGGER.e { "Validation error for ticker $id: $error" }
        return this
    }

    fun exchangeHumanReadable(): String? {
        return when (primaryExchange) {
            "XNAS" -> "NASDAQ"
            "XNYS" -> "NYSE"
            else -> primaryExchange
        }
    }

    fun createTradingViewLink(): String? {
        val exchange = exchangeHumanReadable() ?: return null
        return "https://www.tradingview.com/symbols/$exchange-$id/"
    }

    fun marketCap(): Long {
        return tickerDetails?.marketCap ?: 0
    }

    companion object : WithLogger()
}

data class TickerDetails(
    val description: String?,
    val industry: String?,
    val homepageUrl: String?,
    val totalEmployees: Long?,
    val iconUrl: String?,
    val listDate: LocalDate?,
    val marketCap: Long?,
    val shareClassSharesOutstanding : Long?,
    val weightedSharesOutstanding: Long?,
) {

    fun getAbbreviatedMarketCap(): String? {
        if (marketCap == null) {
            return null
        }

        return BillionToHumanReadableString.convert(marketCap)
    }

    fun industryHumanReadable(): String? {
        return when (industry) {
            "ELECTRONIC COMPUTERS" -> "Elektronik"
            "CRUDE PETROLEUM & NATURAL GAS" -> "Öl & Gas"
            "FIRE, MARINE & CASUALTY INSURANCE" -> "Versicherung"
            "SERVICES-PREPACKAGED SOFTWARE" -> "Software und IT Dienstleistungen"
            else -> industry
        }
    }

    fun listDateHumanReadable(): String? {
        val formatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.GERMAN)
        return listDate?.format(formatter)
    }

    fun descriptionShortened(shortenDescription: Boolean): String? {
        if (!shortenDescription) return description

        return description?.let {
            if (it.length > 250) {
                it.substring(0, 250) + "..."
            } else {
                it
            }
        }
    }
}

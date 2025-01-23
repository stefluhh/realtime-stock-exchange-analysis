package de.stefluhh.stockpicker.stockprice

import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

// Enum zur Steuerung der Schwankungsstärke
enum class RandomStockpriceFuzzyness {
    LOW, MEDIUM, HIGH
}

object RandomStockpriceGenerator {

    fun generateRandomPrices(
        ticker: Symbol,
        from: Instant,
        to: Instant,
        fuzzyness: RandomStockpriceFuzzyness
    ): List<Stockprice> {

        val randomPrices = mutableListOf<Stockprice>()
        var currentTime = from

        // Startpreis und Volumen festlegen
        var currentPrice = Random.nextDouble(100.0, 200.0) // Startpreis zwischen 100 und 200
        val startVolume = 1000    // Startvolumen zwischen 1000 und 5000
        var currentTradeCount = Random.nextLong(100, 1000) // Startanzahl der Trades zwischen 100 und 1000

        // Schwankungsfaktoren basierend auf der Fuzziness
        val (priceVolatility, volumeVolatility, tradeVolatility) = when (fuzzyness) {
            RandomStockpriceFuzzyness.LOW -> Triple(0.01, 0.2, 0.1)
            RandomStockpriceFuzzyness.MEDIUM -> Triple(0.01, 0.4, 0.3)
            RandomStockpriceFuzzyness.HIGH -> Triple(0.01, 0.8, 0.6)
        }

        // Zufällige Preise für jeden Tag innerhalb des Zeitraums generieren
        while (currentTime <= to) {
            // Preisänderungen für den Tag berechnen
            val dailyChange = Random.nextDouble(-priceVolatility, priceVolatility)
            val openPrice = currentPrice * (1 + dailyChange)
            val highPrice = openPrice * (1 + Random.nextDouble(0.0, priceVolatility))
            val lowPrice = openPrice * (1 - Random.nextDouble(0.0, priceVolatility))
            val closePrice = openPrice * (1 + Random.nextDouble(-priceVolatility, priceVolatility))

            // Handelsvolumen und Trade-Anzahl anpassen
            val currentVolume = (startVolume * (1 + Random.nextDouble(-volumeVolatility, volumeVolatility))).toLong().coerceAtLeast(0)
            currentTradeCount = (currentTradeCount * (1 + Random.nextDouble(-tradeVolatility, tradeVolatility))).toLong().coerceAtLeast(0)

            // Erzeugung der Stockprice-Instanz
            val stockprice = Stockprice(
                id = StockpriceId(),
                symbol = ticker,
                date = currentTime,
                priceOpen = BigDecimal.valueOf(openPrice),
                priceClose = BigDecimal.valueOf(closePrice),
                priceLow = BigDecimal.valueOf(lowPrice),
                priceHigh = BigDecimal.valueOf(highPrice),
                tradeCount = currentTradeCount,
                biggestSingleTradeVolume = 42,
                volume = currentVolume,
                aggregatedAt = currentTime
            )

            // Stockprice zur Liste hinzufügen
            randomPrices.add(stockprice)

            // Preis für den nächsten Tag auf den Schlusskurs setzen
            currentPrice = closePrice

            // Zeit um einen Tag erhöhen
            currentTime = currentTime.plusSeconds(60)
        }

        return randomPrices
    }
}


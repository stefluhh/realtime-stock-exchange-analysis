package de.stefluhh.stockanalysis.stockprice.adapter.stockpricestream.trades

data class ConditionProperties(
    val updateHighLow: Boolean,
    val updateLast: Boolean,
    val updateVolume: Boolean,
    val isVolumeCorrection: Boolean = false
)

data class AggregatedTrade(
    var totalVolume: Long = 0L,
    var tradeCount: Long = 0L,
    var biggestTrade: Long = 0L,
    var exchangeBiggestTrade: String? = null,
    var highPrice: Double = Double.MIN_VALUE,
    var lowPrice: Double = Double.MAX_VALUE,
    var openPrice: Double? = null,
    var closePrice: Double? = null,
) {
    /**
     * Prüft ob die Aggregation bereits Werte enthält
     */
    fun isPopulated(): Boolean {
        return totalVolume > 0L && lowPrice > 0.0 && highPrice > 0.0
    }
}

val conditionPropertiesMap: Map<Int, ConditionProperties> = mapOf(
    0 to ConditionProperties(updateHighLow = true, updateLast = true, updateVolume = true),  // Regular Sale
    1 to ConditionProperties(updateHighLow = true, updateLast = true, updateVolume = true),  // Acquisition
    2 to ConditionProperties(updateHighLow = false, updateLast = false, updateVolume = true), // Average Price Trade
    3 to ConditionProperties(updateHighLow = true, updateLast = true, updateVolume = true),  // Automatic Execution
    4 to ConditionProperties(updateHighLow = true, updateLast = true, updateVolume = true),  // Bunched Trade
    5 to ConditionProperties(updateHighLow = true, updateLast = false, updateVolume = true), // Bunched Sold Trade
    7 to ConditionProperties(updateHighLow = false, updateLast = false, updateVolume = true), // Cash Sale
    8 to ConditionProperties(updateHighLow = true, updateLast = true, updateVolume = true),  // Closing Prints
    9 to ConditionProperties(updateHighLow = true, updateLast = true, updateVolume = true),  // Cross Trade
    10 to ConditionProperties(updateHighLow = true, updateLast = false, updateVolume = true), // Derivatively Priced
    11 to ConditionProperties(updateHighLow = true, updateLast = true, updateVolume = true), // Distribution
    12 to ConditionProperties(updateHighLow = false, updateLast = false, updateVolume = true), // Form T
    13 to ConditionProperties(updateHighLow = false, updateLast = false, updateVolume = true), // Extended Trading Hours (Sold Out of Sequence)
    14 to ConditionProperties(updateHighLow = true, updateLast = true, updateVolume = true), // Intermarket Sweep
    15 to ConditionProperties(updateHighLow = false, updateLast = false, updateVolume = false), // Market Center Official Close
    16 to ConditionProperties(updateHighLow = false, updateLast = false, updateVolume = false), // Market Center Official Open
    20 to ConditionProperties(updateHighLow = false, updateLast = false, updateVolume = true), // Next Day
    21 to ConditionProperties(updateHighLow = false, updateLast = false, updateVolume = true), // Price Variation Trade
    22 to ConditionProperties(updateHighLow = true, updateLast = false, updateVolume = true), // Prior Reference Price
    25 to ConditionProperties(updateHighLow = true, updateLast = true, updateVolume = true), // Opening Prints
    27 to ConditionProperties(updateHighLow = true, updateLast = true, updateVolume = true), // Stopped Stock (Regular Trade)
    28 to ConditionProperties(updateHighLow = true, updateLast = true, updateVolume = true), // Re-Opening Prints
    29 to ConditionProperties(updateHighLow = true, updateLast = false, updateVolume = true), // Seller
    30 to ConditionProperties(updateHighLow = true, updateLast = true, updateVolume = true), // Sold Last
    33 to ConditionProperties(updateHighLow = true, updateLast = false, updateVolume = true), // Sold (out of Sequence)
    34 to ConditionProperties(updateHighLow = true, updateLast = true, updateVolume = true), // Split Trade
    36 to ConditionProperties(updateHighLow = true, updateLast = true, updateVolume = true), // Yellow Flag Regular Trade
    37 to ConditionProperties(updateHighLow = false, updateLast = false, updateVolume = true), // Odd Lot Trade
    38 to ConditionProperties(updateHighLow = true, updateLast = true, updateVolume = false), // Corrected Consolidated Close (per listing market)
    44 to ConditionProperties(updateHighLow = false, updateLast = false, updateVolume = false, isVolumeCorrection = true), // Cancelled
    46 to ConditionProperties(updateHighLow = false, updateLast = false, updateVolume = false, isVolumeCorrection = true), // Correction
    52 to ConditionProperties(updateHighLow = false, updateLast = false, updateVolume = true), // Contingent Trade
    53 to ConditionProperties(updateHighLow = false, updateLast = false, updateVolume = true)  // Qualified Contingent Trade ("QCT")

)
package de.stefluhh.stockanalysis.stockprice.analysis.strategies

import java.time.Instant

/**
 * @param strategy The strategy that was used to analyze the stock price
 * @param timeIntervalType The time interval type that was analyzed, i.e. the type of candles that were used
 * @param signal The signal that was detected
 * @param valueOfAnalysisSubject The value of the analysis subject, e.g. the volume of the stock price or the difference in percent in volume detected
 * @param confidence The confidence of the signal, i.e. how strong we believe the strategy detected a significant signal
 */
data class AnalysisResult(
    val symbol: String,
    val date: Instant,
    val signal: Signal,
    val strategy: StrategyId,
    val timeIntervalType: TimeIntervalType,
    val valueOfAnalysisSubject: Double? = null,
    val confidence: Double? = null,
    val debugComments: String? = null
)

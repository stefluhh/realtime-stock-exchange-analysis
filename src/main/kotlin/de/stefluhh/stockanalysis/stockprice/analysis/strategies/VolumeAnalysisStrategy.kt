package de.stefluhh.stockanalysis.stockprice.analysis.strategies

import de.stefluhh.stockanalysis.config.extension.WithLogger
import de.stefluhh.stockanalysis.config.extension.d
import de.stefluhh.stockanalysis.config.extension.rounded
import de.stefluhh.stockanalysis.config.extension.t
import de.stefluhh.stockanalysis.stockprice.Stockprice
import de.stefluhh.stockanalysis.stockprice.tools.DateAnalysisHelper.isTimeBetween
import de.stefluhh.stockanalysis.stockprice.analysis.strategies.StrategyId.VOLUME_ANOMALY
import de.stefluhh.stockanalysis.stockprice.analysis.strategies.TimeIntervalType.DAILY
import de.stefluhh.stockanalysis.stockprice.analysis.strategies.TimeIntervalType.MINUTELY
import java.time.Instant
import java.time.LocalTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/**
 * Das stärkste Signal was wir gesehen haben war WW mit einer Veränderung von 3200% beim Release der News.
 * 50% geteilt durch 32 sind 1.67.
 */
private const val CONFIDENCE_PER_MULTIPLE = 0.0167
private const val TINY_AVERAGE_VOLUME = 1_000.0
private const val MIN_MARKET_CAP = 50_000_000

/**
 * [/] Done Quickfix 21:20 - 10:00 Uhr sleep - Anfang und Ende von Handelstagen haben i. d. R. erhöhtes Volumen. SMA20 auf 1m Candles erzeugen für den Vortag und gucken ob es wirklich eine Anomalie ist
 * TODOs
 * - Wenn um 22:01 ein starker Ausreißer erkannt wird, sofort buy signal geben (Zahlen sind rausgekommen oder so)
 * - Die Ausreißer Thresholds im Premarket Trading erhöhen (z. B. 10x statt 3x)
 * - RF zu klein, Volumen Outlier unter 10k Volumen. Market Cap ok (22B) aber Volumen des Ausreißers halt trotzdem wenig klein
 * - NTBL Market Cap zu klein (2 Mio), 100% Kurssprünge in 15 Minuten
 * - ZPTA Market Cap zu klein (2 Mio)
 * - MTEM Market Cap zu klein (2 Mio)
 * - JDZG Korrekt erkannt aber kein ersichtlicher Grund für Kurssprung. Market Cap 40 Mio
 * - WINT Market Cap zu klein (3 Mio)
 *
 * - OKE korrekt erkannt, mid cap, Analyst recommendation neu rausgekommen mit Kaufempfehlen. Auch kurz vor Börsenschluss
 * - CAG korrekt erkannt, Zunahme Handelsvolumen vor Börsenschluss 21:35
 * - HUMA korrekt erkannt, jedoch kurz vor Börsenschluss, kleines Cap
 *
 * Wir checken jetzt:
 *
 * - Market Cap > 50Mio
 * - Current trade count > 4
 * - Avg. Volumen > 500 pcs
 * - No alert between 21:20 till 10:00
 *
 * Diffing & Thresholds:
 *
 * Benötigte Signale: min 2, max 5 (je nachdem wie viele Ausreißer vorher)
 *
 * - General:               >  100% ( 2x)
 * - Regular:       initial >  300-500% ( 4-6x), confirmation > 125-200% (2.25-3x)
 * - Tiny Volume:   initial > 1400% (15x), confirmation > 900% (  10x)
 *
 * Special:
 *
 * - Signal sofort bestätigt wenn Preis der Ausreißerkerze > 7.5% Anstieg
 *
 * Sonstiges:
 *
 * - Ausreißer gelten sofort als false positive, wenn nach dem Ausreißer eine Kerze kommt, die weniger als das doppelte Volumen hat.
 * - Ausreißer gelten sofort als false positive, wenn eine Kerze mit weniger als 6 Trades reinkommt.
 * - Ausreißer werden spätestens 10 Minuten nach dem Ausreißer als false positive erkannt und verworfen.
 *
 */
class VolumeAnalysisStrategy(
    private val periodType: TimeIntervalType
) : AnalysisStrategy {

    // Eine Map, um pro Ticker offene Ausreißer zu speichern, die noch bestätigt werden müssen
    private val pendingConfirmations = ConcurrentHashMap<String, PendingConfirmation>()

    override fun strategyId(): StrategyId = VOLUME_ANOMALY
    override fun strategyName(): String = "Anomalie im Handelsvolumen"

    override fun analyze(analyzeInput: AnalysisInput): AnalysisResult? {
        val ticker = analyzeInput.ticker
        val tickerSymbol = ticker.id

        if (ticker.marketCap() < MIN_MARKET_CAP || analyzeInput.currentTradeCount() <= 5) {
            return noSignal(analyzeInput, overrideRemove = true)
        }

        val averageVolume = analyzeInput.before.getVolumeIqr50()?.iqr ?: return noSignal(analyzeInput)

        if (averageVolume < 500) return noSignal(analyzeInput)
        if (analyzeInput.before.volumeOutlierCount50() >= 6) return noSignal(analyzeInput)

        val currentVolume = analyzeInput.current.volume
        val diffInPercent = determineDiff(analyzeInput, periodType) ?: return null

        if (diffInPercent < 1.0) {
            return noSignal(analyzeInput)
        }

        /**
         * Handelt es sich bei diesem Run um einen confirmation check, d. h. ein initialer Ausreißer soll
         * bestätigt werden?
         */
        val isConfirmationCheck = pendingConfirmations.containsKey(tickerSymbol)
        val outliersBefore = getOutliersBefore(isConfirmationCheck, analyzeInput)

        // Für sehr kleine Volumen WESENTLICH höheren Schwellwert testen.
        if (!specialThresholdForTinyVolume(averageVolume, diffInPercent, isConfirmationCheck, outliersBefore)) {
            return noSignal(analyzeInput)
        }

        if (diffInPercent < getOutlierThreshold(isConfirmationCheck, outliersBefore)) {
            return noSignal(analyzeInput)
        }

        if (analyzeInput.current.biggestSingleTradePercent() >= 60) {
            return noSignal(analyzeInput)
        }

        if (isAlertSuppressed(analyzeInput.current.date)) {
            return noSignal(analyzeInput)
        }

        // We have a potential signal!
        return inspectSignal(diffInPercent, tickerSymbol, averageVolume, currentVolume, analyzeInput)
    }

    private fun getOutliersBefore(confirmationCheck: Boolean, analyzeInput: AnalysisInput): Int {
        if (!confirmationCheck) return analyzeInput.before.volumeOutlierCount50()

        return pendingConfirmations[analyzeInput.ticker.id]!!.initialOutlier.volumeOutlierCount50()
    }

    /**
     * Spezieller Threshold für Ticker mit sehr geringem Handelsvolumen.
     */
    private fun specialThresholdForTinyVolume(
        averageVolume: Double,
        diffInPercent: Double,
        isConfirmationCheck: Boolean,
        outliersBefore: Int
    ): Boolean {
        if (averageVolume > TINY_AVERAGE_VOLUME) return true // No Op

        return diffInPercent >= getOutlierThreshold(isConfirmationCheck, outliersBefore, TINY_AVERAGE_VOLUME)
    }

    private fun isAlertSuppressed(date: Instant): Boolean {
        // Check if 21:20 - 10 Uhr
        return isTimeBetween(date, LocalTime.of(21, 20), LocalTime.of(10, 0))
    }

    /**
     *
     * Basis: 300% (4x)
     * Wenn zuvor schon 4 oder mehr Ausreißer auf IQR 50 erkannt wurden, dann +200% (6x in Maximalausprägung)
     * Wenn das Volumen sehr gering ist, dann +300% (9x in Maximalausprägung)
     *
     * Wenn es sich um einen Bestätigungscheck handelt, dann halbieren wir den Schwellwert.
     */
    private fun getOutlierThreshold(confirmationCheck: Boolean, outliersBefore: Int, averageVolume: Double? = null): Double {
        var base = 3.0

        if (outliersBefore >= 4) {
            base += 2.0
        }

        if (averageVolume != null && averageVolume < TINY_AVERAGE_VOLUME) {
            base += 3.0
        }

        if (confirmationCheck) {
            base /= 2.0
        }

        return base
    }

    private fun inspectSignal(
        diffInPercent: Double,
        ticker: String,
        averageVolume: Double,
        currentVolume: Long,
        analyzeInput: AnalysisInput
    ): AnalysisResult? {

        // TODO rework
//        if (hasStrongPriceIncrease(analyzeInput)) {
//            pendingConfirmations.remove(ticker)
//            return generateBuySignal(
//                ticker = ticker,
//                analyzeInput,
//                diffInPercent,
//                overrideConfidence = 1.1,
//                debugComments = "Current TradeCount: ${analyzeInput.current.tradeCount}. Initial outlier detected at: ${pendingConfirmations[ticker]?.createdAt}"
//                )
//        }

        // Wir haben grundsätzlich einen Match, aber wir warten noch auf Bestätigung
        val pendingConfirmation = pendingConfirmations[ticker]

        if (pendingConfirmation == null) {
            pendingConfirmations[ticker] = PendingConfirmation(0, analyzeInput.current)
            logMatch(ticker, averageVolume, currentVolume, diffInPercent)
            val biggestSingleTradePercent = analyzeInput.current.biggestSingleTradePercent() ?: 0.0

            LOGGER.d { "Biggest trade for ${analyzeInput.current.symbol} is $biggestSingleTradePercent% of all volume. Tradecount: ${analyzeInput.current.tradeCount}" }
            return null // Waiting for confirmation
        }

        pendingConfirmation.confirmations++

        val reqConfirmations = calculateRequiredConfirmations(pendingConfirmation)

        if (pendingConfirmation.confirmations < reqConfirmations) {
            LOGGER.d { "Confirmations bisher: ${pendingConfirmation.confirmations} von $reqConfirmations for ${pendingConfirmation.initialOutlier.symbol}." }
            return null
        }

        LOGGER.t { "Bestätigung für $ticker erhalten. Signal wird generiert nach ${pendingConfirmation.confirmations} Confirmations." }

        val biggestSingleTradePercent = pendingConfirmation.initialOutlier.biggestSingleTradePercent()

        LOGGER.d { "Biggest trade for ${pendingConfirmation.initialOutlier.symbol} is $biggestSingleTradePercent% of all volume. Tradecount: ${analyzeInput.current.tradeCount}" }

        // MATCH CONFIRMED
        val removed = pendingConfirmations.remove(ticker)!!
        return generateBuySignal(ticker, analyzeInput, removed.initialOutlier.getVolumeIqr50()!!.deviationFromCurrent!!)
    }

    private fun calculateRequiredConfirmations(pendingConfirmation: PendingConfirmation): Int {
        val numberOfOutliersBefore = pendingConfirmation.initialOutlier.volumeOutlierCount50()

        if (numberOfOutliersBefore <= 3) {
            return 3
        }

        if (numberOfOutliersBefore in 4..5) {
            return 4
        }

        return 5
    }

    /**
     * Starker Preisanstieg möglicherweise relativ. Bei einem Small Cap springt der Preis stärker als bei
     * einem Large Cap. Weil das so ist, muss der Signalgeber ggfs. unterschiedlich gewählt werden.
     */
    private fun hasStrongPriceIncrease(analyzeInput: AnalysisInput): Boolean {
        val currentPrice = analyzeInput.current.priceClose
        val beforePrice = analyzeInput.before.priceClose
        val priceDiffInPercent = (currentPrice / beforePrice).toDouble() - 1.0

        if (priceDiffInPercent > 0.075) {
            LOGGER.d { "${analyzeInput.ticker.id} hat einen starken Preisanstieg. Pass Thru Signal" }
            return true
        }

        return false
    }

    /**
     * Wenn das aktuelle Handelsvolumen um weniger als "nur noch" 50% erhöht ist,
     * dann wollen wir gar nicht mehr auf Bestätigungen warten sondern das potentielle Signal killen.
     * Würden wir dies nicht tun, dann würden wir unaufhörlich auf Bestätigungen warten, die über kurz oder lang
     * irgendwann eintreten würden.
     */
    private fun shouldTearDownPendingConfirmation(analyzeInput: AnalysisInput): Boolean {
        val currentVolume = analyzeInput.current.volume
        val pendingConfirmation = pendingConfirmations[analyzeInput.ticker.id]!!
        val averageVolumeBeforeTriggered = pendingConfirmation.initialOutlier.getVolumeIqr50()!!.iqr
        val currentDiffInPercent = (currentVolume / averageVolumeBeforeTriggered) - 1.0

        // Wenn der aktuelle Diff auf keinen Fall mehr als Outlier durchgeht, dann abrechen.
        // Dies ist aktuell der Fall, wenn das aktuelle Handelsvolumen um bis zu 100% erhöht ist.
        // Das ist immer noch mehr als der Durchschnitt. Wir geben dem Ticker also Chance, doch nochmal heftig nach oben
        // auszubrechen.
        if (currentDiffInPercent < 1.0) {
            LOGGER.d { "Abbruch des Ausreißers für ${analyzeInput.ticker.id}. Der aktuelle Diff beträgt ${
                (currentDiffInPercent * 100).rounded(2)
            }% und liegt damit weit unter dem Schwellwert von wenigstens 100%." }
            return true
        }

        val secondsPassed = (System.currentTimeMillis() - pendingConfirmation.createdAt.toEpochMilli()) / 1000

        if (shouldCancelByTime(secondsPassed)) {
            LOGGER.d { "Abbruch des Ausreißers für ${analyzeInput.ticker.id}. Der Ausreißer ist bereits $secondsPassed Sekunden alt." }
            return true
        }

        return false
    }

    private fun noSignal(analyzeInput: AnalysisInput, overrideRemove : Boolean = false): AnalysisResult? {
        if (pendingConfirmations.containsKey(analyzeInput.ticker.id)) {
            if (overrideRemove || shouldTearDownPendingConfirmation(analyzeInput)) {
                pendingConfirmations.remove(analyzeInput.ticker.id)
            }
        }

        return null
    }

    private fun shouldCancelByTime(secondsPassed: Long): Boolean {
        return when (periodType) {
            MINUTELY -> secondsPassed > 10 * 60 // 10 Minuten
            DAILY -> secondsPassed > 60 * 60 * 48 // 48 Stunden
            else -> secondsPassed > 3 * 60 * 60 // 3 Stunden
        }
    }

    private fun generateBuySignal(
        ticker: String,
        analyzeInput: AnalysisInput,
        diffInPercent: Double,
        overrideConfidence : Double? = null,
        debugComments: String? = null
    ): AnalysisResult {
        return AnalysisResult(
            symbol = ticker,
            date = analyzeInput.current.date,
            signal = Signal.BUY,
            strategy = strategyId(),
            timeIntervalType = periodType,
            valueOfAnalysisSubject = diffInPercent,
            confidence = overrideConfidence ?: min(1.0, 0.5 + CONFIDENCE_PER_MULTIPLE * diffInPercent),
            debugComments = debugComments
        )
    }

    private fun logMatch(
        ticker: String,
        averageVolume: Double,
        currentVolume: Long,
        diffInPercent: Double,
    ) {
        LOGGER.t {
            "Signal für $ticker erkannt. Warte auf Bestätigung. (Avg Volume: $averageVolume. Current Volume: $currentVolume. Diff: ${
                (diffInPercent * 100).rounded(
                    2
                )
            }%."
        }
    }

    private fun determineDiff(analyzeInput: AnalysisInput, periodType: TimeIntervalType): Double? =
        when (periodType) {
            MINUTELY -> analyzeInput.current.getVolumeIqr50()?.deviationFromCurrent
            else -> null
        }

    // Klasse, um die Perioden und die Ausreißer zu speichern
    private data class PendingConfirmation(
        var confirmations: Int,
        var initialOutlier: Stockprice,
        val createdAt: Instant = Instant.now()
    )

    companion object : WithLogger()
}
package de.stefluhh.stockanalysis.stockprice.tools

import de.stefluhh.stockanalysis.stockprice.IQR
import de.stefluhh.stockanalysis.stockprice.Stockprice
import kotlin.math.max

object MedianCalculator {


    /**
     * In einer Zahlenreihe von 1,2,3 ist der Median 2.
     * In einer Zahlenreihe von 1,2,3,4,5 ist der Median 3.
     * In einer ausreichend großen Zahlenreihe ist der Median ein sehr performantes Maß für die statistische Mitte der Zahlenreihe.
     */
    fun calculateMedian(sortedData: List<Long>): Double? {
        if (sortedData.isEmpty() || sortedData.size < 3) return null
        val size = sortedData.size
        return if (size % 2 == 0) {
            (sortedData[size / 2 - 1].toDouble() + sortedData[size / 2].toDouble()) / 2
        } else {
            sortedData[size / 2].toDouble()
        }
    }

    /**
     * Der IQR (Interquartile range) findet einen durchschnittlichen Wert, bereinigt um mögliche Ausreißer nach oben und unten.
     * Hierfür wird aus einer Zahlenreihe die mittleren 50% der Werte berechnet.
     * Der IQR ist die Differenz zwischen der oberen Range der 50% und der unteren Range der 50%.
     * Er ist ähnlich wie der median zu verstehen, jedoch wesentlich präziser.
     *                    .----.
     *                  .'      '.
     *                 /          \
     *                |            \
     *                |             |
     *               /              \
     *             /                 \
     *           -                    -
     *   -1.5σ   -σ  Q1     IQR   Q3   +σ   +1.5σ
     *    |      |    |      |      |   |      |
     * ---+------+----+------+------+---+------+------>
     *
     *
     */
    fun calculateIQR(sortedData: List<Stockprice>, ticks: Int, volume: Long): IQR? {
        if (sortedData.isEmpty() || sortedData.size < 3) return null
        val data = sortedData.takeLast(ticks).map { it.volume }.sorted()
        val size = data.size
        val q1 = calculateMedian(data.subList(0, size / 2)) ?: return null
        val q3 = calculateMedian(data.subList((size + 1) / 2, size)) ?: return null
        val iqr = q3 - q1
        val deviationFromCurrent = if (iqr == 0.0) 0.0 else max(0.0, (volume / iqr) - 1.0)
        return IQR(q1, q3, q3 - q1, deviationFromCurrent)
    }

}
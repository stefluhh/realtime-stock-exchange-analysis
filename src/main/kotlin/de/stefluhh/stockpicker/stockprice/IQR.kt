package de.stefluhh.stockpicker.stockprice

/**
 * Wenn der aktuelle Wert um mehr als 4 IQRs vom IQR entfernt ist, handelt es sich um einen signifikanten Ausreißer.
 * Ein Wert von 4 beschreibt bspw. starke Ausreißer vom IQR, ab einem Wert von 6 handelt es sich um extreme Ausreißer.
 * Beispiel: Eine Aktie hat ein relativ konstantes Handelsvolumen von 5000 Stück pro Minute.
 * Das Volumen schwankt im Schnitt zwischen 2500 und 7500. Wenn das Handelsvolumen nun plötzlich auf 20.000 Stück pro Minute steigt,
 * handelt es sich um einen signifikanten Ausreißer.
 *
 * Wir arbeiten mit dem IQR statt dem Durchschnitt (arithmetisches Mittel), da der IQR robust gegenüber Ausreißern ist.
 * Während das arithm. Mittel durch extreme Ausreißer sehr stark nach oben "verfälscht" wird, bleibt der IQR hiervon
 * so gut wie unberührt.
 */
private const val IQR_SIGNIFICANT_OUTLIER_THRESHOLD = 2.5

data class IQR(
    val lower: Double,
    val upper: Double,
    val iqr: Double,
    val deviationFromCurrent: Double? = null,
    val outlierCount: Int? = null
) {

    fun isSignificantOutlier(): Boolean {
        return deviationFromCurrent != null && deviationFromCurrent >= IQR_SIGNIFICANT_OUTLIER_THRESHOLD
    }

    fun outlierCount(): Int {
        return outlierCount ?: 0
    }

    fun accumulateHistoricOutliers(historicIqrs: List<IQR?>): IQR {
        return copy(outlierCount = historicIqrs.count { it?.isSignificantOutlier() == true })

    }
}
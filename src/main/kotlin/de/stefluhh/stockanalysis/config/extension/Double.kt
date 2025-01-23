package de.stefluhh.stockanalysis.config.extension

import java.math.BigDecimal

fun Double.rounded(decimalPlaces: Int): Double {
    if (this.isNaN()) return 0.0

    return this.toBigDecimal().toMoney().toDouble()
}

fun Double.toMoney(): BigDecimal {
    return toBigDecimal().toMoney()
}
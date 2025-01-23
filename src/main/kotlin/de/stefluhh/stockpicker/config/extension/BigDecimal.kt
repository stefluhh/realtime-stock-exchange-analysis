package de.stefluhh.stockpicker.config.extension

import java.math.BigDecimal
import java.math.RoundingMode.HALF_UP

fun List<BigDecimal>.average(): BigDecimal {
    return if (isEmpty()) {
        MONEY_ZERO
    } else {
        (sumOf { it } / BigDecimal(size)).toMoney()
    }
}

fun BigDecimal.toMoney(): BigDecimal {
    return setScale(2, HALF_UP)
}

val MONEY_ZERO = 0.0.toMoney()
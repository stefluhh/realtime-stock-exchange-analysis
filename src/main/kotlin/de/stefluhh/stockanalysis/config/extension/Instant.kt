package de.stefluhh.stockanalysis.config.extension

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

fun Instant.toLocalDate(): LocalDate {
    return LocalDate.ofInstant(this, ZoneId.of("UTC"))
}
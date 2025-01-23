package de.stefluhh.stockpicker.api.dto

import java.time.Instant

data class NewsDto(
    val id: String,
    val category: String,
    val title: String,
    val ticker: String?,
    val reason: String?,
    val rating: Double?,
    val date: Instant,
    val correctedRating: Double?,
    val correctedCategory: String?,
    val correctedReason: String?
)
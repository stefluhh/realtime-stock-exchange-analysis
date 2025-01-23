package de.stefluhh.stockpicker.stockprice.adapter.stockpricestream

import com.fasterxml.jackson.annotation.JsonProperty

data class TradeDto(
    @JsonProperty("ev") val eventType: String? = null,
    @JsonProperty("sym") val ticker: String? = null,
    @JsonProperty("x") val exchangeId: Long? = null,
    @JsonProperty("z") val tape: String? = null,
    @JsonProperty("p") val price: Double? = null,
    @JsonProperty("s") val size: Integer? = null,
    @JsonProperty("t") val timestampMillis: Long? = null,
    @JsonProperty("q") val sequenceNumber: Long? = null,
    @JsonProperty("trft") val trfTimestampMillis: Long? = null
) {
}
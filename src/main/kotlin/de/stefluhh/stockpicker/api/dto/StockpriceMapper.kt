package de.stefluhh.stockpicker.api.dto

import de.stefluhh.stockpicker.config.extension.toMoney
import de.stefluhh.stockpicker.stockprice.Stockprice
import org.bson.types.ObjectId

object StockpriceMapper {


    fun toStockprices(data: List<StockpriceDto>): List<Stockprice> {
        return data.map {
            Stockprice(
                id = ObjectId(),
                symbol = it.ticker,
                date = it.date,
                priceOpen = it.open.toMoney(),
                priceHigh = it.high.toMoney(),
                priceLow = it.low.toMoney(),
                priceClose = it.close.toMoney(),
                volume = it.volume.toLong(),
                biggestSingleTradeVolume = it.biggestSingleTradeVolume,
                tradeCount = it.tradeCount.toLong(),
                aggregatedAt = it.date
            )
        }
    }

}
package de.stefluhh.stockpicker.stockprice

import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.data.domain.Sort
import org.springframework.data.domain.Sort.Direction.ASC
import org.springframework.data.domain.Sort.Direction.DESC
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository

@Repository
class MinuteIntervalStockpriceRepository(
    private val mongoTemplate: MongoTemplate,
    private val tickeRepository: TickerRepository
) : AbstractStockpriceRepository(mongoTemplate, tickerRepository = tickeRepository) {

    @EventListener(ApplicationReadyEvent::class)
    fun initIndices() {
        ensureIndices()
    }

    override fun expiresAfterDays(): Long? {
        return 2
    }

    override fun collectionName(): String {
        return "stockprice_minute_interval"
    }

}
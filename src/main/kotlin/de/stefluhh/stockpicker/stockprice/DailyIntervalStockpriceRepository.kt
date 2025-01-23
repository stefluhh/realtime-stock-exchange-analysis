package de.stefluhh.stockpicker.stockprice

import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.data.domain.Sort.Direction.ASC
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.stereotype.Repository

@Repository
class DailyIntervalStockpriceRepository(
    private val mongoTemplate: MongoTemplate,
    private val tickeRepository: TickerRepository
) : AbstractStockpriceRepository(mongoTemplate, tickerRepository = tickeRepository) {

    @EventListener(ApplicationReadyEvent::class)
    fun started() {
        ensureIndices()
    }

    /**
     * No expiry
     */
    override fun expiresAfterDays(): Long? = null

    override fun collectionName(): String {
        return "stockprice_daily"
    }

}
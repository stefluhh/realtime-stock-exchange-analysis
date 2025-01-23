package de.stefluhh.stockanalysis.stockprice

import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Repository

@Repository
class ThirtyMinuteIntervalStockpriceRepository(
    private val mongoTemplate: MongoTemplate,
    private val tickerRepository: TickerRepository
) : AbstractStockpriceRepository(mongoTemplate, tickerRepository = tickerRepository) {

    @EventListener(ApplicationReadyEvent::class)
    fun initIndices() {
        ensureIndices()
    }

    override fun expiresAfterDays(): Long? {
        return 20
    }

    override fun collectionName(): String {
        return "stockprice_thirtyminute_interval"
    }


}
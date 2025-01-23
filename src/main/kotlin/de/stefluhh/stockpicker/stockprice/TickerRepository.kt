package de.stefluhh.stockpicker.stockprice

import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.cache.annotation.CachePut
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.event.EventListener
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.index.TextIndexDefinition.TextIndexDefinitionBuilder
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.TextCriteria
import org.springframework.data.mongodb.core.query.TextQuery
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository

@Repository
class TickerRepository(private val mongoTemplate: MongoTemplate) {

    @CachePut("tickers")
    fun save(it: Ticker): Ticker {
        return mongoTemplate.save(it)
    }

    @Cacheable("tickers")
    fun findById(id: String): Ticker? {
        return mongoTemplate.findById(id, Ticker::class.java)
    }

    fun existsById(id: String): Boolean {
        return findById(id) != null
    }

    @Cacheable("activeTickers")
    fun fetchAllActiveTickers(): List<Ticker> {
        val query = Query(Criteria.where("active").isEqualTo(true))
        return mongoTemplate.find(query, Ticker::class.java)
    }

    @Cacheable("tickersByName")
    fun findByCompanyName(ticker: String): Ticker? {
        val query = TextQuery(TextCriteria.forDefaultLanguage().matching(ticker)).sortByScore().limit(1)
        return mongoTemplate.findOne(query, Ticker::class.java)
    }

    @EventListener(ApplicationReadyEvent::class)
    fun initIndices() {
        val indexOps = mongoTemplate.indexOps(Ticker::class.java)
        indexOps.ensureIndex(Index().named("active").on("active", Sort.Direction.ASC))
        indexOps.ensureIndex(TextIndexDefinitionBuilder().named("textindex_name").onField("name").build())
    }

    fun delete(symbol: String) {
        val query = Query(Criteria.where("id").isEqualTo(symbol))
        mongoTemplate.remove(query, Ticker::class.java)
    }

}

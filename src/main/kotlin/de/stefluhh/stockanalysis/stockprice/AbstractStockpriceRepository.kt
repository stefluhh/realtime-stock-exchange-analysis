package de.stefluhh.stockanalysis.stockprice

import de.stefluhh.stockanalysis.config.extension.WithLogger
import de.stefluhh.stockanalysis.config.extension.d
import de.stefluhh.stockanalysis.config.extension.e
import de.stefluhh.stockanalysis.config.extension.t
import de.stefluhh.stockanalysis.service.NavigableDateMap
import de.stefluhh.stockanalysis.service.toNavigableDateMap
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.domain.Sort
import org.springframework.data.domain.Sort.Direction.ASC
import org.springframework.data.domain.Sort.Direction.DESC
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap


@Repository
abstract class AbstractStockpriceRepository(
    private val mongoTemplate: MongoTemplate,
    private val tickerRepository: TickerRepository,
) : StockpriceRepository {

    private var cacheWarmed = false
    private val lock = Any()
    private val fastCache: MutableMap<Symbol, ArrayList<Stockprice>> = ConcurrentHashMap()
    private val treeCache: MutableMap<Symbol, NavigableDateMap<Instant, Stockprice>> = ConcurrentHashMap()


    override fun insertAll(candles: List<Stockprice>) {
        LOGGER.d { "Inserting.. ${candles.size}" }
        try {
            mongoTemplate.insert(candles, collectionName())
        } catch (de: DuplicateKeyException) {
            LOGGER.e(de) { "Duplicate key exception. Dropping duplicates and retrying.."}
            insertUniques(candles)
        }

        LOGGER.d { "Inserted. Putting to cache.." }
        synchronized(lock) {
            candles.forEach { putToCache(it) }
            populateNavigableDateMap(candles)

        }
        LOGGER.d { "Put to cache." }
    }

    private fun populateNavigableDateMap(candles: List<Stockprice>) {
        treeCache.putAll(candles.toNavigableDateMap())
    }

    private fun insertUniques(candles: List<Stockprice>) {
        candles.groupBy { it.symbol to it.date }.values.forEach {
            try {
                mongoTemplate.insert(it.first())
            } catch (de: DuplicateKeyException) {
                LOGGER.e { "Duplicate key exception. Not inserting ${it.first()}"}
            }
        }
    }

    // TODO pagination
    fun warmCache(symbol: Symbol? = null) {
        if (cacheWarmed) {
            return
        }

        val activeTickers = if (symbol != null) listOfNotNull(tickerRepository.findByCompanyName(symbol)?.id) else tickerRepository.fetchAllActiveTickers().map(Ticker::id)

        activeTickers.parallelStream().forEach { ticker ->
            val query = query(Criteria.where("symbol").isEqualTo(ticker))
                .with(Sort.by(DESC, "date"))
                .limit(500)

            val result = mongoTemplate.find(
                query,
                Stockprice::class.java,
                collectionName()
            )

            val sorted = result.sortedBy(Stockprice::date)
            synchronized(lock) {
                sorted.forEach { putToCache(it) }
                populateNavigableDateMap(sorted)
            }
        }

        cacheWarmed = true
    }

    override fun findLastNStockPrices(
        symbol: Symbol,
        amount: Int,
        skipLast: Boolean,
        beforeDate: Instant?
    ): List<Stockprice> {
        if (amount > 500) {
            throw IllegalArgumentException("Amount must be less than 500")
        }

        return findLastNStockpricesInternal(symbol, amount, skipLast, beforeDate)
    }

    /**
     * @param skipLast if true, the last stockprice is not included in the result
     */
    private fun findLastNStockpricesInternal(symbol: Symbol, amount: Int, skipLast: Boolean, beforeDate: Instant?): List<Stockprice> {

        if (skipLast && beforeDate != null) {
            throw IllegalArgumentException("skipLast and beforeDate cannot be used together")
        }

        if (beforeDate != null) {
            val list = treeCache[symbol] ?: return emptyList()
            return list.before(beforeDate)?.values?.toList() ?: emptyList()
        }

        val list = fastCache[symbol] ?: return emptyList()

        if (list.size <= amount) {
            return if (skipLast) list.subList(0, list.size - 1) else list
        }

        return if (skipLast) {
            list.subList(list.size - amount - 1, list.size - 1)
        } else {
            list.subList(list.size - amount, list.size)
        }
    }

    private fun putToCache(sp: Stockprice) {

        val list = fastCache.computeIfAbsent(sp.symbol) { ArrayList() }
        list.add(sp)
        if (list.size == 900) {
            LOGGER.t { "Truncating cache to size of 500" }
            fastCache[sp.symbol] = ArrayList(list.subList(400, 900))
        }
    }

    fun ensureIndices() {
        val indexOps = mongoTemplate.indexOps(collectionName())
        indexOps.ensureIndex(Index().named("symbol_date").on("symbol", ASC).on("date", ASC).unique())

        val dateIndex = Index().named("date").on("date", ASC)
        if (expiresAfterDays() != null) {
            dateIndex.expire(expiresAfterDays()!! * 24 * 60 * 60)
        }

        indexOps.ensureIndex(dateIndex)
    }

    abstract fun expiresAfterDays(): Long?


    abstract fun collectionName(): String

    fun getAllFromCache(symbol: String): List<Stockprice> {
        return Collections.unmodifiableList(fastCache[symbol] ?: emptyList())
    }

    fun deleteAll(symbol: String) {
        mongoTemplate.remove(query(Criteria.where("symbol").isEqualTo(symbol)), collectionName())
        fastCache.remove(symbol)
    }

    fun findAll(symbol: String?) : Map<Symbol, List<Stockprice>> {
        val query = if (symbol != null) {
            query(Criteria.where("symbol").isEqualTo(symbol))
        } else {
            query(Criteria())
        }

        return mongoTemplate.find(query, Stockprice::class.java, collectionName()).groupBy(Stockprice::symbol)
    }

    fun save(stockprice: Stockprice?) {
        if (stockprice == null) return
        mongoTemplate.save(stockprice, collectionName())
        putToCache(stockprice)
        treeCache.computeIfAbsent(stockprice.symbol) { NavigableDateMap() }[stockprice.date] = stockprice
    }

    fun clearCalculations(symbol: String?) {

        val query = if (symbol != null) {
            query(Criteria.where("symbol").isEqualTo(symbol))
        } else {
            query(Criteria())
        }

        val update = Update().unset("priceSmas").unset("volumeSmas").unset("volumeIqr").unset("gap")
        mongoTemplate.updateMulti(query, update, collectionName())
    }


    companion object : WithLogger()

}
package de.stefluhh.stockpicker.service

import de.stefluhh.stockpicker.stockprice.Stockprice
import de.stefluhh.stockpicker.stockprice.Symbol
import java.io.Serial
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class NavigableDateMap<Instant, T> : TreeMap<Instant, T>() {

    /**
     * Returns all T behind date
     */
    fun after(key: Instant): NavigableDateMap<Instant, T>? {
        val subMap = tailMap(key, false)
        return subMap.toNavigableDateMap()
    }

    fun before(date: Instant): NavigableDateMap<Instant, T>? {
        return headMap(date, false).toNavigableDateMap()
    }

    companion object {
        @Serial
        private const val serialVersionUID: Long = 7887448103520436242L
    }

}

fun <K, V> Map<K, V>.toNavigableDateMap(): NavigableDateMap<K, V>? {
    return if (isEmpty()) null else NavigableDateMap<K, V>().apply { putAll(this@toNavigableDateMap) }
}

fun List<Stockprice>.toNavigableDateMap(): ConcurrentHashMap<Symbol, NavigableDateMap<Instant, Stockprice>> {
    val result : ConcurrentHashMap<Symbol, NavigableDateMap<Instant, Stockprice>> = ConcurrentHashMap()
    forEach {
        val symbol = it.symbol
        if (result[symbol] == null) {
            result[symbol] = NavigableDateMap()
        }
        result[symbol]!![it.date] = it
    }
    return result
}
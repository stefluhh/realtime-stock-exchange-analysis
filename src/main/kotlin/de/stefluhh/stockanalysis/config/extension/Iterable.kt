package de.stefluhh.stockanalysis.config.extension

import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

// Extension function for parallel map processing
suspend fun <T, R> Iterable<T>.parallelMap(transform: suspend (T) -> R): List<R> = coroutineScope {
    // Launch coroutines for each element in the iterable and map results in parallel
    map { element ->
        async(Default) { transform(element) }
    }.awaitAll() // Await the result of all coroutines
}

// Extension function for parallel flatMap processing
suspend fun <T, R> Iterable<T>.parallelFlatMap(transform: suspend (T) -> Iterable<R>): List<R> = coroutineScope {
    // Launch coroutines for each element and flatten results in parallel
    map { element ->
        async(Default) { transform(element) }
    }.awaitAll() // Await the result of all coroutines
        .flatten() // Flatten the list of lists into a single list
}
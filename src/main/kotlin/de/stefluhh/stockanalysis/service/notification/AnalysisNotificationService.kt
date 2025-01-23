package de.stefluhh.stockanalysis.service.notification

import de.stefluhh.stockanalysis.config.extension.WithLogger
import de.stefluhh.stockanalysis.config.extension.i
import de.stefluhh.stockanalysis.stockprice.analysis.strategies.AnalysisResult
import jakarta.annotation.PreDestroy
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicInteger

@Service
class AnalysisNotificationService {

    private val subscribers = mutableListOf<AnalysisNotificationSubscriber>()

    private val notifying = AtomicInteger(0)

    @EventListener(ApplicationReadyEvent::class)
    fun subscribeOurselves() {
        subscribe(object : AnalysisNotificationSubscriber {
            override fun onAnalysisResult(result: AnalysisResult) {
                LOGGER.i { "Analysis result: $result" }
            }
        })
    }

    fun notify(analysisResults: List<AnalysisResult>) {
        notifying.incrementAndGet()
        try {
            subscribers.parallelStream().forEach { subscriber ->
                analysisResults.forEach { result ->
                    subscriber.onAnalysisResult(result)
                }
            }
        } finally {
            notifying.decrementAndGet()
        }
    }

    fun subscribe(subscriber: AnalysisNotificationSubscriber) {
        subscribers.add(subscriber)
    }

    @PreDestroy
    fun shutDown() {
        LOGGER.i { "Shutting down AnalysisNotificationService..."}
        while (notifying.get() > 0) {
            Thread.sleep(100)
        }
        LOGGER.i { "AnalysisNotificationService shut down."}
    }

    companion object : WithLogger()
}
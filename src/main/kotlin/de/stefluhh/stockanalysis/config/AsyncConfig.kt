package de.stefluhh.stockanalysis.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.AsyncConfigurer
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration
class AsyncConfig : AsyncConfigurer {

    override fun getAsyncExecutor() = ThreadPoolTaskExecutor().apply {
        corePoolSize = 2
        maxPoolSize = 2
        queueCapacity = 50000
        setThreadNamePrefix("stockprice-")
        initialize()
    }

}
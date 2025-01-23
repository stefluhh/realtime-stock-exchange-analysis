package de.stefluhh.stockpicker

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EnableCaching
@EnableAsync
class StockpickerApplication

fun main(args: Array<String>) {
    SpringApplication(StockpickerApplication::class.java).run(*args)
}

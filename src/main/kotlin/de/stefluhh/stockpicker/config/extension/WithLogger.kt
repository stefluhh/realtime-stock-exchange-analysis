package de.stefluhh.stockpicker.config.extension

import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class WithLogger {
    @Suppress("PropertyName")
    protected val LOGGER: Logger = LoggerFactory.getLogger(javaClass)
}
package de.stefluhh.stockanalysis.config.extension

import org.slf4j.Logger

inline fun Logger.t(throwable: Throwable? = null, crossinline messageSupplier: () -> String) {
    if (isTraceEnabled) {
        trace(messageSupplier(), throwable)
    }
}

inline fun Logger.d(throwable: Throwable? = null, crossinline messageSupplier: () -> String) {
    if (isDebugEnabled) {
        debug(messageSupplier(), throwable)
    }
}

inline fun Logger.i(throwable: Throwable? = null, crossinline messageSupplier: () -> String) {
    if (isInfoEnabled) {
        info(messageSupplier(), throwable)
    }
}

inline fun Logger.w(throwable: Throwable? = null, crossinline messageSupplier: () -> String) {
    if (isWarnEnabled) {
        warn(messageSupplier(), throwable)
    }
}

inline fun Logger.e(throwable: Throwable? = null, crossinline messageSupplier: () -> String) {
    if (isErrorEnabled) {
        error(messageSupplier(), throwable)
    }
}

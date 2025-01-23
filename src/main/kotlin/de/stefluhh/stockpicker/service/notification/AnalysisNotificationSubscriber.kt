package de.stefluhh.stockpicker.service.notification

import de.stefluhh.stockpicker.stockprice.analysis.strategies.AnalysisResult

interface AnalysisNotificationSubscriber {

    fun onAnalysisResult(result: AnalysisResult)

}

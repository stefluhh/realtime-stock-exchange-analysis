package de.stefluhh.stockanalysis.service.notification

import de.stefluhh.stockanalysis.stockprice.analysis.strategies.AnalysisResult

interface AnalysisNotificationSubscriber {

    fun onAnalysisResult(result: AnalysisResult)

}

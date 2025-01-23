package de.stefluhh.stockpicker.stockprice.analysis.strategies

interface AnalysisStrategy {

    fun strategyId() : StrategyId
    fun analyze(analyzeInput: AnalysisInput) : AnalysisResult?
    fun strategyName() : String

}

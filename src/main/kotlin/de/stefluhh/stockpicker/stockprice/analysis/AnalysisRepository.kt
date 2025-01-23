package de.stefluhh.stockpicker.stockprice.analysis

import de.stefluhh.stockpicker.stockprice.analysis.strategies.AnalysisResult
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Repository

@Repository
class AnalysisRepository(private val mongoTemplate: MongoTemplate) {

    fun insertAll(results: List<AnalysisResult>) {
        mongoTemplate.insertAll(results)
    }


}

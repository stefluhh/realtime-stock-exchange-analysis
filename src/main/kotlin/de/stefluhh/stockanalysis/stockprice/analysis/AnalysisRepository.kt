package de.stefluhh.stockanalysis.stockprice.analysis

import de.stefluhh.stockanalysis.stockprice.analysis.strategies.AnalysisResult
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Repository

@Repository
class AnalysisRepository(private val mongoTemplate: MongoTemplate) {

    fun insertAll(results: List<AnalysisResult>) {
        mongoTemplate.insertAll(results)
    }


}

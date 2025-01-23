package de.stefluhh.stockpicker.stockprice.adapter

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.polygon.kotlin.sdk.DefaultOkHttpClientProvider
import io.polygon.kotlin.sdk.HttpClientProvider
import io.polygon.kotlin.sdk.rest.PolygonRestClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PolygonIOConfig(
    @Value("\${application.polygon-io.apiKey}") private val apiKey: String,
) {


    @Bean
    fun polygonIORestClient(): PolygonRestClient {
        return PolygonRestClient(apiKey, httpClientProvider = httpClientWithTimeout())
    }

    private fun httpClientWithTimeout(): HttpClientProvider {
        return object : DefaultOkHttpClientProvider() {
            override fun buildClient(): HttpClient {
                return super.buildClient().config {
                    install(HttpTimeout) {
                        requestTimeoutMillis = 10_000
                        connectTimeoutMillis = 10_000
                        socketTimeoutMillis = 10_000
                    }
                }
            }
        }
    }

}
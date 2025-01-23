## Realtime stock exchange trade analyzer

This is a hobby project that I work on intermittently. It focuses on the algorithmic analysis of stock prices and trading volumes for various purposes, such as anomaly detection.

From a technical perspective, it is a Spring Boot application built with Kotlin and MongoDB.

I use Polygon.io as the data provider, specifically the Trades WebSocket API. Trades represent individual stock exchange transactions. This application is capable of streaming all trades from U.S. exchanges in real time, aggregating them into different time intervals (currently 1-minute and 30-minute candlesticks), and running analysis strategies on the generated candlesticks.

## Trades, not aggregates

Unlike applications that rely on pre-aggregated data (see the *Dark Pool Filter* section below), this application processes all individual trades from U.S. stock exchanges. Each trade corresponds to a single stock transaction. This results in processing up to 20,000 trades per second, especially during high-activity periods near the market close.

### Darkpool filter

Polygon.io offers aggregated stockprice and volumes on a minutely interval both as REST Api as well as WS: [WebSocket Aggregates (minutely)](https://polygon.io/docs/stocks/ws_stocks_am).
However the problem with these is, that they contain trades from some [https://en.wikipedia.org/wiki/Dark_pool](dark pool) stock exchanges,
which are used mainly for professional trading by financial institutions. Trade volumes on these exchanges are so large, that no meaningful
analysis is possible due to too much noise. Hence, the `StockpriceStreamingAdapter` streams all trades from all stock exchanges instead and filters
trades out made on dark pool exchanges.

The application is then going ahead aggregating the trades into 1-minute candles itself.

## Analysis

At the end of each minute, the application finalizes a 1-minute candlestick for all incoming stock data (one candlestick per security). This event triggers the `AnalysisService`, which applies various analysis strategies to the stock data. Each strategy can analyze the current candlestick, compare it to the previous one, or use other methods of evaluation.

## VolumeAnalysisStrategy

The volume-based analysis strategy is designed to detect anomalies in the trading volume of securities. Sudden volume increases of several hundred percent—or even several thousand percent—within 1 to 2 minutes often signal that the security warrants further attention.

However, this strategy presents significant challenges. For example, trading volumes naturally spike in the final minutes before the market closes, which the strategy may mistakenly identify as an anomaly and interpret as a buy signal. As a temporary solution, the analysis excludes trades made during the last 40 minutes of the trading session.

Anyone interested is welcome to contribute to this strategy. I have since shifted my focus to a news-based approach for detecting buy signals.

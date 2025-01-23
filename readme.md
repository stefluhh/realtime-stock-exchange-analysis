## Realtime stock exchange trade analyzer

This is a hobby project that I work on from time to time. It deals with the algorithmic analysis of stock prices and stock volumes for different purposes, e.g. anomaly recognition.

Technically, its a Spring Boot application using Kotlin and MongoDB.

I use Polygon.io as a data provider, specifically the Trades WebSocket API. Trades represent each person's stock exchange trade This application is capable of streaming all trades from all U.S. exchanges in real time, aggregating them into different time intervals (currently 1-minute and 30-minute candles), and then running any analysis strategies on the generated 1-minute or 30-minute candles.

## Trades, not aggregates

Since this application doesn't work on pre-aggregated data (see Darkpool section below), this application has to process all US stock exchange's trades itself.
A trade represents every stock exchange transaction from all U.S. exchanges.
This can amount to up to 20,000 trades per second, usually at the end of the day.

### Darkpool filter

Polygon.io offers aggregated stockprice and volumes on a minutely interval both as REST Api as well as WS: [WebSocket Aggregates (minutely)](https://polygon.io/docs/stocks/ws_stocks_am).
However the problem with these is, that they contain trades from some [https://en.wikipedia.org/wiki/Dark_pool](dark pool) stock exchanges,
which are used mainly for professional trading by financial institutions. Trade volumes on these exchanges are so large, that no meaningful
analysis is possible due to too much noise. Hence, the `StockpriceStreamingAdapter` streams all trades from all stock exchanges instead and filters
trades out made on dark pool exchanges.

The application is then going ahead aggregating the trades into 1-minute candles itself.

## Analysis

Every minute, a 1-minute candlestick for all incoming stock data (i.e., one candle per security) is finalized.
This event triggers the `AnalysisService` and runs an analysis on all stock prices.
Each analysis strategy can then either analyze the current candle directly or compare it to the previous candle, etc.

## VolumeAnalysisStrategy

The volume-based analysis strategy focuses on detecting anomalies in the trading volume of a security. Volume increases of several hundred percent,
often even several thousand percent within 1 or 2 minutes, are usually a clear signal that the security warrants closer examination.

However, this strategy requires significantly more effort due to some inherent challenges. For example, trading volume naturally increases sharply
shortly before market close, which the analysis strategy often mistakenly identifies as an anomaly and interprets as a buy signal. As a quick fix,
the analysis is simply not conducted during the last 40 minutes before the market closes.

Anyone interested is welcome to contribute to this strategy, as I have since transitioned to a news-based buy signal detection approach.

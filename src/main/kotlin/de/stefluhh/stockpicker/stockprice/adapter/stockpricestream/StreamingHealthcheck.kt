package de.stefluhh.stockpicker.stockprice.adapter.stockpricestream

import de.stefluhh.stockpicker.config.extension.WithLogger
import de.stefluhh.stockpicker.config.extension.e
import de.stefluhh.stockpicker.config.extension.i
import java.time.*

object StreamingHealthcheck : WithLogger() {

    fun checkHealth(lastMessageReceivedAt: Instant?): Boolean {
        val inMonitoringPeriod = checkIfMonitoringPeriod()

        if (!inMonitoringPeriod) {
            LOGGER.i { "Outside of monitoring period (10:20 - 02:00 CET). No check needed." }
            return true
        }

        if (lastMessageReceivedAt == null) {
            LOGGER.e { "No messages received since streaming started. Is today weekend or all exchanges closed?" }

            if (LocalDate.now().dayOfWeek.value in 1..5) {
                LOGGER.e { "Today is a weekday. Sending alert..." }
            }

            return false
        } else {
            val timeSinceLastMessage = Duration.between(lastMessageReceivedAt, Instant.now())

            if (timeSinceLastMessage.toMinutes() > 15) {
                // Falls seit der letzten Nachricht mehr als 15 Minuten vergangen sind, Reconnect
                LOGGER.e { "No messages received in the last 15 minutes. Reconnecting..." }
                return false
            } else {
                LOGGER.i { "Streaming is active. Last message received ${timeSinceLastMessage.toMinutes()} minutes ago." }
                return true
            }
        }
    }

    private fun checkIfMonitoringPeriod(): Boolean {
        val now = Instant.now()
        val cetZone = ZoneId.of("Europe/Berlin")  // CET/CEST Zeitzone
        val currentTimeInCET = LocalTime.now(cetZone)

        // Definiere den Überwachungszeitraum (10:20 - 02:00 CET, d.h. 08:20 - 00:00 UTC)
        val startOfMonitoring = LocalTime.of(10, 20)  // 10:00 CET
        val endOfMonitoring = LocalTime.of(2, 0)  // 02:00 CET (folgt auf den nächsten Tag)

        // Prüfen, ob wir im Überwachungszeitraum sind
        val inMonitoringPeriod = if (startOfMonitoring.isBefore(endOfMonitoring)) {
            currentTimeInCET.isAfter(startOfMonitoring) && currentTimeInCET.isBefore(endOfMonitoring)
        } else {
            currentTimeInCET.isAfter(startOfMonitoring) || currentTimeInCET.isBefore(endOfMonitoring)
        }

        return inMonitoringPeriod
    }


}
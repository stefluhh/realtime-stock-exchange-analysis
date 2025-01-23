package de.stefluhh.stockpicker.stockprice.tools

object BillionToHumanReadableString {

    fun convert(aLong: Long): String {
        return when {
            aLong >= 1_000_000_000_000L -> String.format("%.2fT", aLong / 1_000_000_000_000.0)
            aLong >= 1_000_000_000L -> String.format("%.2fB", aLong / 1_000_000_000.0)
            aLong >= 1_000_000L -> String.format("%.2fM", aLong / 1_000_000.0)
            aLong >= 1_000L -> String.format("%.2fK", aLong / 1_000.0)
            else -> aLong.toString()
        }
    }

}
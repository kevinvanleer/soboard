package kvl.android.kvl.soboard

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val monthDay = DateTimeFormatter.ofPattern("MMM d", Locale.US)

fun parseBcbp(raw: String, today: LocalDate = LocalDate.now()): String? {
    if (raw.length < 47 || raw[0] != 'M' || !raw[1].isDigit()) return null
    val from = raw.substring(30, 33)
    val to = raw.substring(33, 36)
    val carrier = raw.substring(36, 39).trim()
    val flight = raw.substring(39, 44).trim().trimStart('0')
    val dayOfYear = raw.substring(44, 47).trim().toIntOrNull()
    if (!from.all(Char::isLetter) || !to.all(Char::isLetter) || carrier.isEmpty() || flight.isEmpty()) return null

    val date = dayOfYear?.let { day ->
        (-1..1).mapNotNull { runCatching { LocalDate.ofYearDay(today.year + it, day) }.getOrNull() }
            .minByOrNull { abs(it.toEpochDay() - today.toEpochDay()) }
    }
    return listOfNotNull("$carrier $flight", "$from→$to", date?.format(monthDay)).joinToString(" · ")
}

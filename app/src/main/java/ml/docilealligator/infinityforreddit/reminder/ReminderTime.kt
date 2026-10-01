package ml.docilealligator.infinityforreddit.reminder

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Converts between a reminder's time and what the Material3 date and time pickers hold.
 *
 * DatePickerState names a calendar date by the UTC midnight that starts it, and TimePickerState
 * holds a bare hour and minute, so neither carries a zone. Both directions go through the device
 * zone at the reminder's own date. Applying today's UTC offset instead put a reminder on the other
 * side of a daylight saving change an hour out in the time picker, and saving it again after
 * changing only the date kept the wrong hour.
 */
object ReminderTime {
    /** The calendar date [reminderTime] falls on in [zone], as DatePickerState's UTC-midnight millis. */
    fun toPickerDateMillis(reminderTime: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(reminderTime).atZone(zone).toLocalDate()
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /** The wall-clock time [reminderTime] falls on in [zone]. */
    fun toPickerTime(reminderTime: Long, zone: ZoneId = ZoneId.systemDefault()): LocalTime =
        Instant.ofEpochMilli(reminderTime).atZone(zone).toLocalTime()

    /**
     * The time named by the picked date, hour and minute in [zone]. A wall-clock time a daylight
     * saving change skips moves forward by the length of the gap, and one it repeats takes the
     * earlier of its two instants.
     */
    fun fromPicker(pickerDateMillis: Long, hour: Int, minute: Int, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(pickerDateMillis).atZone(ZoneOffset.UTC).toLocalDate()
            .atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    /**
     * Whether the time picker should run on a 24-hour clock: the clock the app's time format
     * setting shows reminder times in, so the picker and the time it sets agree. [systemIs24Hour]
     * decides for a pattern that shows no hour.
     */
    fun is24Hour(timeFormatPattern: String, systemIs24Hour: Boolean): Boolean {
        // Quoted text is literal, so its letters are not pattern fields.
        val fields = timeFormatPattern.replace(Regex("'[^']*'"), "")
        return when {
            fields.any { it == 'h' || it == 'K' } -> false
            fields.any { it == 'H' || it == 'k' } -> true
            else -> systemIs24Hour
        }
    }
}

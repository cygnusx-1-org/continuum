package ml.docilealligator.infinityforreddit.reminder

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The conversions between a reminder's time and the date and time pickers. Every case is in a zone
 * with daylight saving, at dates whose offset differs from part of the year, because applying one
 * fixed offset is the mistake these replace.
 */
class ReminderTimeTest {

    private val newYork = ZoneId.of("America/New_York")

    private fun millis(iso: String) = Instant.parse(iso).toEpochMilli()

    private fun utcMidnight(date: String) =
        LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun `a winter reminder shows its own wall-clock time, whatever the offset is today`() {
        // 09:00 EST, UTC-5. Reading it with a summer offset of UTC-4 showed 10:00.
        val reminderTime = millis("2026-11-10T14:00:00Z")

        assertEquals(utcMidnight("2026-11-10"), ReminderTime.toPickerDateMillis(reminderTime, newYork))
        assertEquals(LocalTime.of(9, 0), ReminderTime.toPickerTime(reminderTime, newYork))
    }

    @Test
    fun `a summer reminder shows its own wall-clock time`() {
        // 09:00 EDT, UTC-4.
        val reminderTime = millis("2026-07-10T13:00:00Z")

        assertEquals(utcMidnight("2026-07-10"), ReminderTime.toPickerDateMillis(reminderTime, newYork))
        assertEquals(LocalTime.of(9, 0), ReminderTime.toPickerTime(reminderTime, newYork))
    }

    @Test
    fun `the picker date is the local date, not the UTC one`() {
        // 23:30 EST on the 10th is already the 11th in UTC.
        val reminderTime = millis("2026-11-11T04:30:00Z")

        assertEquals(utcMidnight("2026-11-10"), ReminderTime.toPickerDateMillis(reminderTime, newYork))
        assertEquals(LocalTime.of(23, 30), ReminderTime.toPickerTime(reminderTime, newYork))
    }

    @Test
    fun `picking a date and time names that wall-clock time on that date`() {
        assertEquals(
            millis("2026-11-10T14:00:00Z"),
            ReminderTime.fromPicker(utcMidnight("2026-11-10"), 9, 0, newYork)
        )
        assertEquals(
            millis("2026-07-10T13:00:00Z"),
            ReminderTime.fromPicker(utcMidnight("2026-07-10"), 9, 0, newYork)
        )
    }

    @Test
    fun `a reminder read into the pickers and saved again is unchanged`() {
        for (iso in listOf("2026-01-15T17:45:00Z", "2026-06-15T16:45:00Z", "2026-11-11T04:30:00Z")) {
            val reminderTime = millis(iso)
            val pickerTime = ReminderTime.toPickerTime(reminderTime, newYork)

            assertEquals(
                iso,
                reminderTime,
                ReminderTime.fromPicker(
                    ReminderTime.toPickerDateMillis(reminderTime, newYork),
                    pickerTime.hour,
                    pickerTime.minute,
                    newYork
                )
            )
        }
    }

    @Test
    fun `changing only the date keeps the wall-clock time across a daylight saving change`() {
        // 09:00 EDT moved to a date after the clocks go back is 09:00 EST, not 08:00.
        val summerReminder = millis("2026-10-20T13:00:00Z")
        val pickerTime = ReminderTime.toPickerTime(summerReminder, newYork)

        assertEquals(
            millis("2026-11-10T14:00:00Z"),
            ReminderTime.fromPicker(utcMidnight("2026-11-10"), pickerTime.hour, pickerTime.minute, newYork)
        )
    }

    @Test
    fun `a time the clocks skip moves forward by the gap`() {
        // 02:30 does not exist on 8 March 2026 in New York; 03:30 EDT does.
        assertEquals(
            millis("2026-03-08T07:30:00Z"),
            ReminderTime.fromPicker(utcMidnight("2026-03-08"), 2, 30, newYork)
        )
    }

    @Test
    fun `a time the clocks repeat takes the earlier instant`() {
        // 01:30 happens twice on 1 November 2026 in New York; the first is EDT.
        assertEquals(
            millis("2026-11-01T05:30:00Z"),
            ReminderTime.fromPicker(utcMidnight("2026-11-01"), 1, 30, newYork)
        )
    }

    @Test
    fun `the picker clock follows the time format setting`() {
        assertTrue(ReminderTime.is24Hour("MMM d, yyyy, HH:mm", systemIs24Hour = false))
        assertFalse(ReminderTime.is24Hour("MMM d, yyyy, hh:mm a", systemIs24Hour = true))
        assertTrue(ReminderTime.is24Hour("d.M.yyyy kk:mm", systemIs24Hour = false))
        assertFalse(ReminderTime.is24Hour("d.M.yyyy KK:mm a", systemIs24Hour = true))
    }

    @Test
    fun `quoted letters are not hour fields`() {
        assertTrue(ReminderTime.is24Hour("HH'h'mm", systemIs24Hour = false))
    }

    @Test
    fun `a format without an hour follows the system clock`() {
        assertTrue(ReminderTime.is24Hour("yyyy-MM-dd", systemIs24Hour = true))
        assertFalse(ReminderTime.is24Hour("yyyy-MM-dd", systemIs24Hour = false))
    }
}

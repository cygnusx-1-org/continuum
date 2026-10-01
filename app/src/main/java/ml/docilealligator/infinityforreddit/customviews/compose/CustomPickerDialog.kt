package ml.docilealligator.infinityforreddit.customviews.compose

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import ml.docilealligator.infinityforreddit.R

/**
 * A date picker dialog that changes [state] only when OK is pressed.
 *
 * The picker edits a copy seeded from [state]. Handing it [state] itself applied every tap at once,
 * so Cancel only closed the dialog and the date it was meant to discard was saved anyway.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDatePickerDialog(state: DatePickerState, onDismissRequest: () -> Unit) {
    val editState = rememberDatePickerState(initialSelectedDateMillis = state.selectedDateMillis)

    DatePickerDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            CustomPositiveTextButton(stringResId = R.string.ok) {
                state.selectedDateMillis = editState.selectedDateMillis
                onDismissRequest()
            }
        },
        dismissButton = {
            CustomNeutralTextButton(stringResId = R.string.cancel) {
                onDismissRequest()
            }
        }
    ) {
        DatePicker(state = editState)
    }
}

/**
 * A time picker dialog that changes [state] only when OK is pressed, for the same reason as
 * [CustomDatePickerDialog].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomTimePickerDialog(state: TimePickerState, onDismissRequest: () -> Unit) {
    val editState = rememberTimePickerState(
        initialHour = state.hour,
        initialMinute = state.minute,
        is24Hour = state.is24hour
    )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        dismissButton = {
            CustomNeutralTextButton(stringResId = R.string.cancel) {
                onDismissRequest()
            }
        },
        confirmButton = {
            CustomPositiveTextButton(stringResId = R.string.ok) {
                state.hour = editState.hour
                state.minute = editState.minute
                onDismissRequest()
            }
        },
        text = {
            TimePicker(state = editState)
        }
    )
}

package ml.docilealligator.infinityforreddit.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ml.docilealligator.infinityforreddit.reminder.Reminder
import ml.docilealligator.infinityforreddit.reminder.ReminderManager

class RemindersViewModel(
    private val reminderManager: ReminderManager
) : ViewModel() {
    private val _reminders = MutableStateFlow<List<Reminder>?>(null)
    val reminders = _reminders.asStateFlow()

    suspend fun initializeReminders() {
        reminderManager.getAllRemindersFlow().collect {
            _reminders.value = it
        }
    }

    /**
     * [onResult] receives the other reminder already set for the same post or comment at
     * [newReminderTime], in which case nothing changed, or null once [reminder] has moved.
     */
    fun updateReminder(reminder: Reminder, newReminderTime: Long, onResult: (existingReminder: Reminder?) -> Unit) {
        viewModelScope.launch {
            onResult(reminderManager.updateReminder(reminder, newReminderTime))
        }
    }

    fun deleteReminder(reminder: Reminder) {
        viewModelScope.launch {
            reminderManager.deleteReminder(reminder)
        }
    }

    companion object {
        fun provideFactory(
            reminderManager: ReminderManager
        ) : ViewModelProvider.Factory {
            return object: ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras
                ): T {
                    return RemindersViewModel(reminderManager) as T
                }
            }
        }
    }
}
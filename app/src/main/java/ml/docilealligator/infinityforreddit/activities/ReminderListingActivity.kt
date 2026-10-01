package ml.docilealligator.infinityforreddit.activities

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults.enterAlwaysScrollBehavior
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ml.docilealligator.infinityforreddit.Infinity
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper
import ml.docilealligator.infinityforreddit.customviews.compose.AppTheme
import ml.docilealligator.infinityforreddit.customviews.compose.CustomDatePickerDialog
import ml.docilealligator.infinityforreddit.customviews.compose.CustomFilledButton
import ml.docilealligator.infinityforreddit.customviews.compose.CustomNegativeTextButton
import ml.docilealligator.infinityforreddit.customviews.compose.CustomPositiveTextButton
import ml.docilealligator.infinityforreddit.customviews.compose.CustomTimePickerDialog
import ml.docilealligator.infinityforreddit.customviews.compose.LocalAppTheme
import ml.docilealligator.infinityforreddit.customviews.compose.LocalTypography
import ml.docilealligator.infinityforreddit.customviews.compose.PrimaryText
import ml.docilealligator.infinityforreddit.customviews.compose.SecondaryText
import ml.docilealligator.infinityforreddit.customviews.compose.ThemedTopAppBar
import ml.docilealligator.infinityforreddit.reminder.Reminder
import ml.docilealligator.infinityforreddit.reminder.ReminderManager
import ml.docilealligator.infinityforreddit.reminder.ReminderTime
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils
import ml.docilealligator.infinityforreddit.utils.Utils
import ml.docilealligator.infinityforreddit.viewmodels.RemindersViewModel
import ml.docilealligator.infinityforreddit.viewmodels.RemindersViewModel.Companion.provideFactory
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Named

class ReminderListingActivity : BaseActivity() {
    @Inject
    @Named("default")
    lateinit var mSharedPreferences: SharedPreferences
    @Inject
    @Named("post_layout")
    lateinit var mPostLayoutSharedPreferences: SharedPreferences
    @Inject
    @Named("current_account")
    lateinit var mCurrentAccountSharedPreferences: SharedPreferences
    @Inject
    lateinit var mCustomThemeWrapper: CustomThemeWrapper
    @Inject
    lateinit var mReminderManager: ReminderManager

    lateinit var mViewModel: RemindersViewModel

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        ((application) as Infinity).appComponent.inject(this)

        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (isImmersiveInterfaceRespectForcedEdgeToEdge()) {
                enableEdgeToEdge()
            }
        }

        mViewModel = ViewModelProvider.create(
            this,
            provideFactory(mReminderManager)
        )[RemindersViewModel::class.java]

        val windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = customThemeWrapper.isLightStatusBar

        val calendar = Calendar.getInstance()
        val timeFormat = mSharedPreferences.getString(
            SharedPreferencesUtils.TIME_FORMAT_KEY,
            SharedPreferencesUtils.TIME_FORMAT_DEFAULT_VALUE
        ) ?: SharedPreferencesUtils.TIME_FORMAT_DEFAULT_VALUE
        val formatter = DateTimeFormatter.ofPattern(timeFormat, Locale.getDefault())
        val is24Hour = ReminderTime.is24Hour(timeFormat, DateFormat.is24HourFormat(this))

        setContent {
            AppTheme(customThemeWrapper.themeType, mSharedPreferences) {
                val context = LocalContext.current
                val scrollBehavior = enterAlwaysScrollBehavior()
                val reminders by mViewModel.reminders.collectAsStateWithLifecycle()
                val haptics = LocalHapticFeedback.current

                var showReminderOptionSheet by remember { mutableStateOf(false) }
                val reminderOptionSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                var reminderToBeEditedOrDeleted: Reminder? by remember { mutableStateOf(null) }
                var showEditReminderDateDialog by remember { mutableStateOf(false) }
                var showEditReminderTimeDialog by remember { mutableStateOf(false) }
                val datePickerState = rememberDatePickerState()
                val timePickerState = rememberTimePickerState(
                    initialHour = calendar.get(Calendar.HOUR_OF_DAY),
                    initialMinute = calendar.get(Calendar.MINUTE),
                    is24Hour = is24Hour,
                )

                var reminderTimeMillis: Long by remember {
                    mutableLongStateOf(System.currentTimeMillis() + 60 * 60 * 24 * 1000)
                }
                var reminderTimeString: String by remember {
                    mutableStateOf("")
                }

                LaunchedEffect(Unit) {
                    mViewModel.initializeReminders()
                }

                val editReminder: (Reminder) -> Unit = { reminder ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    reminderToBeEditedOrDeleted = reminder
                    datePickerState.selectedDateMillis = ReminderTime.toPickerDateMillis(reminder.reminderTime)

                    val pickerTime = ReminderTime.toPickerTime(reminder.reminderTime)
                    timePickerState.hour = pickerTime.hour
                    timePickerState.minute = pickerTime.minute
                    reminderTimeMillis = reminder.reminderTime

                    showReminderOptionSheet = true
                }

                LaunchedEffect(timePickerState.hour, timePickerState.minute, datePickerState.selectedDateMillis) {
                    datePickerState.selectedDateMillis?.let {
                        reminderTimeMillis = ReminderTime.fromPicker(it, timePickerState.hour, timePickerState.minute)
                        val instant = Instant.ofEpochMilli(reminderTimeMillis)
                        reminderTimeString = formatter.withZone(ZoneId.systemDefault()).format(instant)
                    } ?: run {
                        reminderTimeMillis = 0
                        reminderTimeString = ""
                    }
                }

                Scaffold(
                    topBar = {
                        ThemedTopAppBar(
                            titleStringResId = R.string.reminders,
                            isImmersiveInterfaceEnabled = isImmersiveInterfaceEnabled,
                            scrollBehavior = scrollBehavior,
                            windowInsetsController = windowInsetsController
                        ) {
                            finish()
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .imePadding(),
                    contentWindowInsets = if (isImmersiveInterfaceEnabled) WindowInsets.safeDrawing else WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                ) { innerPadding ->
                    reminders?.let {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(LocalAppTheme.current.backgroundColor)),
                            contentPadding = innerPadding
                        ) {
                            item {
                                Spacer(Modifier.height(16.dp))
                            }
                            // The primary key, so unique; distinct reminders can share a hashCode,
                            // and a repeated key throws.
                            items(it, key = {
                                "${it.postId} ${it.commentId} ${it.reminderTime}"
                            } ) { reminder ->
                                if (reminder.commentId.isEmpty()) {
                                    PostReminder(
                                        Modifier
                                            .padding(horizontal = 16.dp)
                                            .padding(bottom = 16.dp),
                                        reminder,
                                        onClick = {
                                            startActivity(
                                                Intent(context, ViewPostDetailActivity::class.java).apply {
                                                    putExtra(ViewPostDetailActivity.EXTRA_POST_ID, reminder.postId)
                                                }
                                            )
                                        },
                                        onLongClick = {
                                            editReminder(reminder)
                                        }
                                    )
                                } else {
                                    CommentReminder(
                                        Modifier
                                            .padding(horizontal = 16.dp)
                                            .padding(bottom = 16.dp),
                                        reminder,
                                        onClick = {
                                            startActivity(
                                                Intent(context, ViewPostDetailActivity::class.java).apply {
                                                    putExtra(ViewPostDetailActivity.EXTRA_POST_ID, reminder.postId)
                                                    putExtra(ViewPostDetailActivity.EXTRA_SINGLE_COMMENT_ID, reminder.commentId)
                                                }
                                            )
                                        },
                                        onLongClick = {
                                            editReminder(reminder)
                                        }
                                    )
                                }
                            }
                        }
                    } ?: run {
                        Box(modifier = Modifier
                            .fillMaxSize()
                            .background(Color(LocalAppTheme.current.backgroundColor))
                        )
                    }

                    if (showReminderOptionSheet) {
                        ModalBottomSheet(
                            containerColor = Color(LocalAppTheme.current.backgroundColor),
                            onDismissRequest = {
                                showReminderOptionSheet = false
                            },
                            sheetState = reminderOptionSheetState
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(vertical = 16.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    PrimaryText(
                                        R.string.reminder_time,
                                        modifier = Modifier
                                            .padding(horizontal = 16.dp),
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.weight(1f))

                                    PrimaryText(
                                        reminderTimeString,
                                        modifier = Modifier
                                            .padding(end = 16.dp),
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp)
                                        .padding(horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    CustomPositiveTextButton(
                                        stringResId = R.string.set_date
                                    ) {
                                        showEditReminderDateDialog = true
                                    }

                                    CustomPositiveTextButton(
                                        stringResId = R.string.set_time
                                    ) {
                                        showEditReminderTimeDialog = true
                                    }

                                    CustomNegativeTextButton(
                                        stringResId = R.string.delete
                                    ) {
                                        showReminderOptionSheet = false
                                        reminderToBeEditedOrDeleted?.let {
                                            mViewModel.deleteReminder(it)
                                        }
                                    }
                                }

                                CustomFilledButton(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .padding(top = 4.dp),
                                    stringResId = R.string.ok
                                ) {
                                    if (reminderTimeMillis == reminderToBeEditedOrDeleted?.reminderTime) {
                                        showReminderOptionSheet = false
                                        return@CustomFilledButton
                                    }
                                    if (reminderTimeMillis < System.currentTimeMillis()) {
                                        Toast.makeText(context, R.string.reminder_time_must_be_in_future, Toast.LENGTH_SHORT).show()
                                        return@CustomFilledButton
                                    }
                                    reminderToBeEditedOrDeleted?.let {
                                        mViewModel.updateReminder(it, reminderTimeMillis) { existingReminder ->
                                            if (existingReminder == null) {
                                                showReminderOptionSheet = false
                                            } else {
                                                // Left open so another time can be picked.
                                                Toast.makeText(
                                                    context,
                                                    if (existingReminder.commentId.isEmpty()) R.string.reminder_already_set_for_post
                                                    else R.string.reminder_already_set_for_comment,
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (showEditReminderDateDialog) {
                        CustomDatePickerDialog(datePickerState) {
                            showEditReminderDateDialog = false
                        }
                    }

                    if (showEditReminderTimeDialog) {
                        CustomTimePickerDialog(timePickerState) {
                            showEditReminderTimeDialog = false
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun PostReminder(modifier: Modifier, reminder: Reminder, onClick: () -> Unit, onLongClick: () -> Unit) {
        val context = LocalContext.current
        val remainingTimeText by remember {
            mutableStateOf(getRemainingTimeText(context, reminder.reminderTime))
        }

        Column(
            modifier = modifier
                .fillMaxSize(1f)
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .background(Color(LocalAppTheme.current.filledCardViewBackgroundColor))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize(1f)
                    .padding(bottom = 8.dp)
            ) {
                PrimaryText(R.string.post)

                Spacer(modifier = Modifier.weight(1f))

                PrimaryText(remainingTimeText)
            }

            SecondaryText(reminder.content, fontSize = LocalTypography.current.titleFontSize.default)
        }
    }

    @Composable
    private fun CommentReminder(modifier: Modifier, reminder: Reminder, onClick: () -> Unit, onLongClick: () -> Unit) {
        val context = LocalContext.current
        val remainingTimeText by remember {
            mutableStateOf(getRemainingTimeText(context, reminder.reminderTime))
        }

        Column(
            modifier = modifier
                .fillMaxSize(1f)
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .background(Color(LocalAppTheme.current.filledCardViewBackgroundColor))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize(1f)
                    .padding(bottom = 8.dp)
            ) {
                PrimaryText(R.string.comment)

                Spacer(modifier = Modifier.weight(1f))

                PrimaryText(remainingTimeText)
            }

            SecondaryText(reminder.content, fontSize = LocalTypography.current.titleFontSize.default)
        }
    }

    fun getRemainingTimeText(context: Context, time: Long): String {
        val diff = time - System.currentTimeMillis()

        if (diff <= 0) return "Expired"

        val minutes = diff / Utils.MINUTE_MILLIS
        val hours = diff / Utils.HOUR_MILLIS
        val days = diff / Utils.DAY_MILLIS
        val months = diff / Utils.MONTH_MILLIS
        val years = diff / Utils.YEAR_MILLIS

        return when {
            years > 1 -> context.getString(R.string.remaining_time_in_years, years)
            years == 1L -> context.getString(R.string.remaining_time_1_year)
            months > 1 -> context.getString(R.string.remaining_time_in_months, months)
            months == 1L -> context.getString(R.string.remaining_time_1_month)
            days > 1 -> context.getString(R.string.remaining_time_in_days, days)
            days == 1L -> context.getString(R.string.remaining_time_1_day)
            hours > 1 -> context.getString(R.string.remaining_time_in_hours, hours)
            hours == 1L -> context.getString(R.string.remaining_time_1_hour)
            minutes > 1 -> context.getString(R.string.remaining_time_in_minutes, minutes)
            minutes == 1L -> context.getString(R.string.remaining_time_1_minute)
            else -> context.getString(R.string.remaining_time_less_than_1_minute)
        }
    }

    override fun getDefaultSharedPreferences(): SharedPreferences {
        return mSharedPreferences
    }

    override fun getCurrentAccountSharedPreferences(): SharedPreferences {
        return mCurrentAccountSharedPreferences
    }

    override fun getCustomThemeWrapper(): CustomThemeWrapper {
        return mCustomThemeWrapper
    }

    override fun applyCustomTheme() {

    }
}
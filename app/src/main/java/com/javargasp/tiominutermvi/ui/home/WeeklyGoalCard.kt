package com.javargasp.tiominutermvi.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.domain.goal.CurrentWeekSummary
import com.javargasp.tiominutermvi.domain.traininglog.CalendarDates
import com.javargasp.tiominutermvi.domain.traininglog.LocalDateProvider
import com.javargasp.tiominutermvi.ui.theme.extendedColors
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Home's compact "This week" card: title and "3 of 4 workouts" on one line, the progress
 * bar, the seven days of the week and one message line (with the streak when there is
 * one), plus "See history" and "Log activity". Without a goal: an invitation, "Set goal",
 * "See history" and "Log activity". Tapping the card or "Set goal" calls [onClick] (the
 * goal screen); "See history" calls [onSeeHistory]; "Log activity" calls [onLogActivity].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WeeklyGoalCompactCard(
    week: CurrentWeekSummary,
    message: WeeklyGoalMessage?,
    onClick: () -> Unit,
    onSeeHistory: () -> Unit,
    onLogActivity: () -> Unit,
    modifier: Modifier = Modifier
) {
    val openLabel = stringResource(R.string.weekly_goal_card_open)
    // A plain Card made clickable, so the click action carries a label for TalkBack
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardDefaults.shape)
            .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val goal = week.progress.goal
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.weekly_goal_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (goal != null) {
                    // The text can go past the goal ("5 of 4 workouts"); the bar stops at 100 %
                    Text(
                        text = pluralStringResource(R.plurals.weekly_goal_progress, goal, week.progress.workoutCount, goal),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            if (goal == null) {
                Text(
                    text = stringResource(R.string.weekly_goal_card_no_goal),
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                Column(
                    modifier = Modifier.padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { (week.progress.workoutCount.toFloat() / goal).coerceAtMost(1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    WeekDaysRow(week)
                    WeeklyMessageLine(message = message, streak = week.streak)
                }
            }
            if (goal == null) {
                Button(onClick = onClick) { Text(stringResource(R.string.weekly_goal_card_set_goal)) }
            }
            // Wraps to a second line when both don't fit
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onLogActivity, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.log_activity_button))
                }
                TextButton(onClick = onSeeHistory, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.home_see_history))
                }
            }
        }
    }
}

/** "1 workout left to reach your goal · Streak: 2 weeks" — one line; success color when met. */
@Composable
private fun WeeklyMessageLine(message: WeeklyGoalMessage?, streak: Int) {
    val messageText = when (message) {
        WeeklyGoalMessage.GoalMet -> stringResource(R.string.weekly_goal_met)
        is WeeklyGoalMessage.WorkoutsLeft ->
            pluralStringResource(R.plurals.weekly_goal_workouts_left, message.workouts, message.workouts)
        null -> null
    }
    val streakText = if (streak >= 1) pluralStringResource(R.plurals.weekly_goal_streak, streak, streak) else null
    val text = listOfNotNull(messageText, streakText).joinToString(" · ")
    if (text.isEmpty()) return
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (message == WeeklyGoalMessage.GoalMet) {
            MaterialTheme.extendedColors.success
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    )
}

/**
 * Monday to Sunday: filled on days with counted workouts (with the number when 2 or more,
 * a check for 1), today outlined; read as one sentence by TalkBack.
 */
@Composable
private fun WeekDaysRow(week: CurrentWeekSummary) {
    val dates = CalendarDates.weekDates(week.progress.weekStart)
    val letters = listOf(
        R.string.weekly_day_short_mon, R.string.weekly_day_short_tue, R.string.weekly_day_short_wed,
        R.string.weekly_day_short_thu, R.string.weekly_day_short_fri, R.string.weekly_day_short_sat,
        R.string.weekly_day_short_sun
    ).map { stringResource(it) }
    val description = weekDescription(week, dates)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        dates.forEachIndexed { index, date ->
            val workouts = week.progress.workoutsOn(date)
            val trained = workouts > 0
            val isToday = date == week.today
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = letters[index], style = MaterialTheme.typography.labelMedium)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .size(28.dp)
                        .background(
                            color = if (trained) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape
                        )
                        .then(
                            if (isToday) {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            } else {
                                Modifier
                            }
                        )
                ) {
                    if (workouts >= 2) {
                        Text(
                            text = "$workouts",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else if (trained) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/** "Monday 1 workout, Wednesday 2 workouts. Today, Friday: no workouts yet." */
@Composable
private fun weekDescription(week: CurrentWeekSummary, dates: List<String>): String {
    val locale = LocalConfiguration.current.locales[0]
    val days = dates.filter { week.progress.workoutsOn(it) > 0 }.map { date ->
        val count = week.progress.workoutsOn(date)
        pluralStringResource(R.plurals.weekly_goal_a11y_day_workouts, count, dayName(date, locale), count)
    }
    val trained = if (days.isEmpty()) {
        stringResource(R.string.weekly_goal_a11y_no_workouts)
    } else {
        days.joinToString(", ")
    }
    val todayName = dayName(week.today, locale)
    val todayCount = week.progress.workoutsOn(week.today)
    val today = if (todayCount > 0) {
        pluralStringResource(R.plurals.weekly_goal_a11y_today_workouts, todayCount, todayName, todayCount)
    } else {
        stringResource(R.string.weekly_goal_a11y_today_no_workouts, todayName)
    }
    return "$trained. $today."
}

/** The full weekday name of a local date in [locale] ("Monday" / "lunes"). */
private fun dayName(date: String, locale: Locale): String {
    val utc = TimeZone.getTimeZone("UTC")
    val parsed = SimpleDateFormat(LocalDateProvider.DATE_PATTERN, Locale.ROOT).apply { timeZone = utc }.parse(date)
        ?: return date
    return SimpleDateFormat("EEEE", locale).apply { timeZone = utc }.format(parsed)
}

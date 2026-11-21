// ignore: unused_import
import 'package:intl/intl.dart' as intl;

import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppLocalizationsEn extends AppLocalizations {
  AppLocalizationsEn([String locale = 'en']) : super(locale);

  @override
  String get appName => 'Tio Minuter';

  @override
  String get tagline => 'No excuses — just 10 minutes a day.';

  @override
  String get landingTitle => 'Your week, your history and your groups, on the web';

  @override
  String get landingText =>
      'Tio Minuter walks you through short daily workouts, gym routines and programs on your Android phone. Sign in with the same Google account to see your weekly goal, your training history and how your groups are doing.';

  @override
  String get landingTrainOnPhone =>
      'Train with the daily routines, your programs and the activities you log, here or in the Android app.';

  @override
  String get signInWithGoogle => 'Sign in with Google';

  @override
  String get signingIn => 'Signing in…';

  @override
  String get signInFailed => 'Couldn\'t sign in with Google. Please try again.';

  @override
  String get noNetwork => 'No internet connection. Check it and try again.';

  @override
  String get popupBlocked => 'The sign-in window was blocked. Allow pop-ups for this site, or try again.';

  @override
  String get navWeek => 'This week';

  @override
  String get navHistory => 'History';

  @override
  String get navGroups => 'Groups';

  @override
  String get navAccount => 'Account';

  @override
  String get weeklyGoalTitle => 'Weekly goal';

  @override
  String get weekNoGoal => 'Set your weekly goal';

  @override
  String get weekNoGoalHint => 'Set it in the Android app: Home → This week.';

  @override
  String weekProgress(int done, int goal) {
    String _temp0 = intl.Intl.pluralLogic(
      done,
      locale: localeName,
      other: '$done of $goal workouts',
      one: '$done of $goal workout',
    );
    return '$_temp0';
  }

  @override
  String get weekGoalMet => 'Goal met this week!';

  @override
  String weekWorkoutsLeft(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count workouts left to reach your goal',
      one: '$count workout left to reach your goal',
    );
    return '$_temp0';
  }

  @override
  String weekStreak(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Streak: $count weeks',
      one: 'Streak: $count week',
    );
    return '$_temp0';
  }

  @override
  String weekCountingRule(int minutes) {
    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other:
          'Any workout of at least $minutes minutes counts: daily routines, gym routines, program workouts and the activities you log. Two workouts on the same day count as two.',
      one:
          'Any workout of at least $minutes minute counts: daily routines, gym routines, program workouts and the activities you log. Two workouts on the same day count as two.',
    );
    return '$_temp0';
  }

  @override
  String weekDayNone(String day) {
    return '$day: no workouts';
  }

  @override
  String weekDaySources(String day, String sources) {
    return '$day: $sources';
  }

  @override
  String get weekToday => 'today';

  @override
  String get historyTitle => 'Training history';

  @override
  String get historyEmpty =>
      'Completed workouts appear here: daily routines, gym routines, program workouts and the activities you log.';

  @override
  String get historyToday => 'Today';

  @override
  String get historyYesterday => 'Yesterday';

  @override
  String get historyNotCounted => 'Doesn\'t count toward the goal';

  @override
  String get historyLoadMore => 'Load more';

  @override
  String durationMinutes(int minutes) {
    return '$minutes min';
  }

  @override
  String get sourceDaily => 'Daily routine';

  @override
  String get sourceGym => 'Gym routine';

  @override
  String get sourceProgram => 'Program';

  @override
  String get sourceActivity => 'Activity';

  @override
  String workoutNameDay(int day) {
    return 'Day $day';
  }

  @override
  String workoutNameDayWithTitle(String day, String title) {
    return '$day · $title';
  }

  @override
  String get programTitleFullBodyA => 'Full body A';

  @override
  String get programTitleFullBodyB => 'Full body B';

  @override
  String get programTitleFullBodyC => 'Full body C';

  @override
  String get programTitleUpperA => 'Upper A';

  @override
  String get programTitleUpperB => 'Upper B';

  @override
  String get programTitleLowerA => 'Lower A';

  @override
  String get programTitleLowerB => 'Lower B';

  @override
  String get programTitlePush => 'Push';

  @override
  String get programTitlePull => 'Pull';

  @override
  String get programTitleLegs => 'Legs';

  @override
  String get programTitleFullBody => 'Full body';

  @override
  String get programTitleUpper => 'Upper';

  @override
  String get programTitleLower => 'Lower';

  @override
  String get programTitleUpperC => 'Upper C';

  @override
  String get programTitleLowerC => 'Lower C';

  @override
  String get programTitleOutdoorRun => 'Outdoor run';

  @override
  String get programTitleBriskWalk => 'Brisk walk';

  @override
  String get programTitleTreadmill => 'Treadmill';

  @override
  String get programTitleHomeCircuit => 'Home circuit';

  @override
  String get activityOwnGymRoutine => 'Own gym routine';

  @override
  String get activityRunning => 'Running';

  @override
  String get activityWalking => 'Walking';

  @override
  String get activityCycling => 'Cycling';

  @override
  String get activitySwimming => 'Swimming';

  @override
  String get activityYoga => 'Yoga';

  @override
  String get activityPilates => 'Pilates';

  @override
  String get activityDance => 'Dance';

  @override
  String get activityTeamSport => 'Team sport';

  @override
  String get activityRacketSport => 'Racket sport';

  @override
  String get activityMartialArts => 'Martial arts';

  @override
  String get activityHiit => 'HIIT';

  @override
  String get activityStretching => 'Stretching';

  @override
  String get activityOther => 'Other';

  @override
  String get groupsTitle => 'Groups';

  @override
  String get groupsEmptyTitle => 'No groups yet';

  @override
  String get groupsOwner => 'Owner';

  @override
  String groupsMemberCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(count, locale: localeName, other: '$count members', one: '$count member');
    return '$_temp0';
  }

  @override
  String get groupsTabRanking => 'Ranking';

  @override
  String get groupsTabFeed => 'Feed';

  @override
  String get groupsThisWeek => 'This week';

  @override
  String get groupsMe => 'You';

  @override
  String groupsProgress(int done, int goal) {
    return '$done/$goal';
  }

  @override
  String groupsStreak(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Streak: $count weeks',
      one: 'Streak: $count week',
    );
    return '$_temp0';
  }

  @override
  String groupsPosition(int position) {
    return '#$position';
  }

  @override
  String get groupsNotUpdated => 'Not updated this week';

  @override
  String get groupsGone => 'This group is no longer available.';

  @override
  String get feedEmpty => 'No workouts shared this week yet.';

  @override
  String get feedEmptyMonday => 'A new week starts today. Workouts your group finishes will show up here.';

  @override
  String get feedLoadMore => 'Load more';

  @override
  String feedPhotoDescription(String name) {
    return 'Workout photo of $name';
  }

  @override
  String feedReactionDescription(String label, int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$label, $count reactions',
      one: '$label, $count reaction',
    );
    return '$_temp0';
  }

  @override
  String get feedReactionMine => 'Your reaction';

  @override
  String get feedReactionNotMine => 'Not selected';

  @override
  String get feedReactionStrong => 'Strong';

  @override
  String get feedReactionFire => 'On fire';

  @override
  String get feedReactionClap => 'Applause';

  @override
  String get feedReactionRaised => 'Celebrate';

  @override
  String get feedJustNow => 'Just now';

  @override
  String get feedReactionFailed => 'Couldn\'t save your reaction';

  @override
  String get accountTitle => 'Account';

  @override
  String get accountPhotoDescription => 'Profile photo';

  @override
  String get accountSignOut => 'Sign out';

  @override
  String get accountSignedOut => 'Signed out.';

  @override
  String get loadFailed => 'Couldn\'t load this. Check your connection and try again.';

  @override
  String get retry => 'Retry';

  @override
  String get loading => 'Loading…';

  @override
  String get navTrain => 'Train';

  @override
  String get trainTitle => 'Train';

  @override
  String trainExercisesCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(count, locale: localeName, other: '$count exercises', one: '$count exercise');
    return '$_temp0';
  }

  @override
  String get trainNoRoutines => 'No routines available';

  @override
  String trainInProgress(String name) {
    return 'Workout in progress: $name';
  }

  @override
  String get trainContinue => 'Continue';

  @override
  String sessionSaved(String duration) {
    return 'Your unfinished workout was saved ($duration).';
  }

  @override
  String sessionDurationHoursMinutes(int hours, String minutes) {
    return '$hours h $minutes min';
  }

  @override
  String sessionDurationMinutes(int minutes) {
    return '$minutes min';
  }

  @override
  String get back => 'Back';

  @override
  String get done => 'Done';

  @override
  String get cancel => 'Cancel';

  @override
  String get pendingWrites => 'Not synced yet';

  @override
  String get pendingWritesDescription =>
      'Your changes are saved in this browser and will be sent when the connection returns.';

  @override
  String get workoutTitle => 'Workout';

  @override
  String workoutExerciseProgress(int current, int total) {
    return 'Exercise $current of $total';
  }

  @override
  String get workoutGetReady => 'Get Ready';

  @override
  String get workoutGetReadyBanner => 'GET READY';

  @override
  String get workoutSkipExercise => 'Skip Exercise';

  @override
  String get workoutNextExercise => 'Next Exercise';

  @override
  String get workoutCompleteReps => 'Complete Reps';

  @override
  String get workoutTimerRunning => 'Timer running…';

  @override
  String get workoutRest => 'Rest';

  @override
  String get workoutSeconds => 'seconds';

  @override
  String workoutReps(int count) {
    String _temp0 = intl.Intl.pluralLogic(count, locale: localeName, other: '$count reps', one: '$count rep');
    return '$_temp0';
  }

  @override
  String get workoutComplete => 'Workout Complete!';

  @override
  String get workoutGreatJob => 'Great job!';

  @override
  String get exerciseTypeTime => 'Timed exercise';

  @override
  String get exerciseTypeReps => 'Repetition exercise';

  @override
  String get exerciseTypeRest => 'Rest';

  @override
  String get unknownExercise => 'Unknown exercise';

  @override
  String get shareInGroups => 'Share in my groups';

  @override
  String get shareInGroupsOn => 'Your groups will see this workout in their feed.';

  @override
  String get shareInGroupsOff => 'This workout stays private.';

  @override
  String get shareIntroTitle => 'Share your workouts with your groups';

  @override
  String get shareIntroText =>
      'When you finish a workout, the members of your groups see it in the group\'s feed: your name and photo, the workout\'s title and how long it took. Notes and intensity are never shared. You can turn sharing off for any workout.';

  @override
  String get shareIntroOk => 'OK';

  @override
  String get shareIntroDontShare => 'Don\'t share this one';

  @override
  String get logActivity => 'Log activity';

  @override
  String get logActivityTitleNew => 'Log an activity';

  @override
  String get logActivityTitleEdit => 'Edit activity';

  @override
  String get logActivityType => 'Activity';

  @override
  String get logActivityCustomName => 'Activity name';

  @override
  String logActivityCounter(int count, int max) {
    return '$count/$max';
  }

  @override
  String get logActivityDuration => 'Duration';

  @override
  String logActivityMinutesShort(int count) {
    String _temp0 = intl.Intl.pluralLogic(count, locale: localeName, other: '$count min', one: '$count min');
    return '$_temp0';
  }

  @override
  String get logActivityMinutesField => 'Minutes';

  @override
  String logActivityShortDurationHint(int minutes) {
    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: 'It won\'t count toward your weekly goal (minimum $minutes minutes)',
      one: 'It won\'t count toward your weekly goal (minimum $minutes minute)',
    );
    return '$_temp0';
  }

  @override
  String get logActivityIntensity => 'Intensity';

  @override
  String get intensityEasy => 'Easy';

  @override
  String get intensityModerate => 'Moderate';

  @override
  String get intensityHard => 'Hard';

  @override
  String get intensityEasyDescription => 'You can talk normally';

  @override
  String get intensityModerateDescription => 'You can talk with effort';

  @override
  String get intensityHardDescription => 'Hard to talk';

  @override
  String logActivityIntensityOption(String label, String description) {
    return '$label: $description';
  }

  @override
  String get logActivityWhen => 'When';

  @override
  String logActivityTime(String time) {
    return 'Time: $time';
  }

  @override
  String logActivityTimeChange(String time) {
    return 'Time: $time. Change the time';
  }

  @override
  String get logActivityNote => 'Note (optional)';

  @override
  String get logActivitySave => 'Save';

  @override
  String get logActivityDelete => 'Delete activity';

  @override
  String get logActivityDeleteTitle => 'Delete this activity?';

  @override
  String get logActivityDeleteMessage => 'It will be removed from the history and the weekly goal.';

  @override
  String get logActivityDeleteConfirm => 'Delete';

  @override
  String get logActivityDiscardTitle => 'Discard changes?';

  @override
  String get logActivityDiscardMessage => 'The changes you made will be lost.';

  @override
  String get logActivityDiscardConfirm => 'Discard';

  @override
  String get logActivityKeepEditing => 'Keep editing';

  @override
  String get logActivityErrorType => 'Choose an activity';

  @override
  String get logActivityErrorName => 'Write the name of the activity';

  @override
  String logActivityErrorTooLong(int max) {
    String _temp0 = intl.Intl.pluralLogic(
      max,
      locale: localeName,
      other: 'Use at most $max characters',
      one: 'Use at most $max character',
    );
    return '$_temp0';
  }

  @override
  String logActivityErrorDuration(int min, int max) {
    String _temp0 = intl.Intl.pluralLogic(
      max,
      locale: localeName,
      other: 'Enter between $min and $max minutes',
      one: 'Enter between $min and $max minute',
    );
    return '$_temp0';
  }

  @override
  String get logActivityErrorIntensity => 'Choose an intensity';

  @override
  String get logActivityErrorFuture => 'The time can\'t be in the future';

  @override
  String logActivityErrorTooEarly(int days) {
    String _temp0 = intl.Intl.pluralLogic(
      days,
      locale: localeName,
      other: 'Choose a day within the last $days days',
      one: 'Choose a day within the last $days day',
    );
    return '$_temp0';
  }

  @override
  String get logActivitySaveFailed => 'The activity could not be saved. Try again.';

  @override
  String get logActivityNotFound => 'This activity no longer exists.';

  @override
  String get weeklyGoalQuestion => 'How many workouts a week do you want to do?';

  @override
  String weeklyGoalWorkoutsPerWeek(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count workouts per week',
      one: '$count workout per week',
    );
    return '$_temp0';
  }

  @override
  String get weeklyGoalFewer => 'Fewer workouts';

  @override
  String get weeklyGoalMore => 'More workouts';

  @override
  String get weeklyGoalAppliesFromThisWeek => 'The change applies from this week; past weeks keep their goal.';

  @override
  String get weeklyGoalSave => 'Save';

  @override
  String get weeklyGoalSet => 'Set goal';

  @override
  String get weeklyGoalChange => 'Change goal';

  @override
  String get historyDeleteAction => 'Delete entry';

  @override
  String get historyDeleteTitle => 'Delete this entry?';

  @override
  String historyDeleteMessage(String name) {
    return '\"$name\" will be removed from the history.';
  }

  @override
  String get historyDeleteConfirm => 'Delete';

  @override
  String get historyEditActivity => 'Edit activity';

  @override
  String get programsTitle => 'My programs';

  @override
  String get programsEmptyTitle => 'No programs yet';

  @override
  String get programsEmptyText => 'Create a training plan of several weeks based on your age, weight, level and goal.';

  @override
  String get programCreate => 'Create program';

  @override
  String get programGoalGainMuscle => 'Gain strength and muscle';

  @override
  String get programGoalLoseFat => 'Lose fat';

  @override
  String get programLevelBeginner => 'Beginner';

  @override
  String get programLevelIntermediate => 'Intermediate';

  @override
  String get programLevelAdvanced => 'Advanced';

  @override
  String get programLocationHome => 'Home';

  @override
  String get programLocationGym => 'Gym';

  @override
  String programCardMeta(String first, String second) {
    return '$first · $second';
  }

  @override
  String programProgress(String percent) {
    return 'Progress: $percent %';
  }

  @override
  String get programCreateTitle => 'New program';

  @override
  String get programAgeLabel => 'Age';

  @override
  String get programWeightLabel => 'Weight (kg)';

  @override
  String get programHeightLabel => 'Height (cm)';

  @override
  String get programErrorRequired => 'Required';

  @override
  String programErrorRange(int min, int max) {
    return 'Enter a value from $min to $max';
  }

  @override
  String programHintRange(int min, int max) {
    return 'From $min to $max';
  }

  @override
  String get programLevelLabel => 'Level';

  @override
  String get programGoalLabel => 'Goal';

  @override
  String get programLocationLabel => 'Where you train';

  @override
  String get programFocusLabel => 'Focus';

  @override
  String get programFocusBalanced => 'Balanced';

  @override
  String get programFocusBalancedDescription => 'The whole body, evenly';

  @override
  String get programFocusLowerBody => 'Lower body';

  @override
  String get programFocusLowerBodyDescription => 'More glutes and legs';

  @override
  String get programFocusUpperBody => 'Upper body';

  @override
  String get programFocusUpperBodyDescription => 'More chest, back, shoulders and arms';

  @override
  String get programFocusCore => 'Core and stability';

  @override
  String get programFocusCoreDescription => 'More core and balance work';

  @override
  String programFocusValue(String focus) {
    return 'Focus: $focus';
  }

  @override
  String get programWeeksLabel => 'Weeks';

  @override
  String get programDaysLabel => 'Days per week';

  @override
  String programDecrease(String label) {
    return 'Decrease $label';
  }

  @override
  String programIncrease(String label) {
    return 'Increase $label';
  }

  @override
  String programBmi(String value) {
    return 'BMI: $value';
  }

  @override
  String get programLowImpactNote =>
      'Your plan uses low-impact exercises: no jumping or running, and walking instead of running.';

  @override
  String get programUnderweightWarning =>
      'Your BMI is below 18.5. Consider choosing “Gain strength and muscle” instead of “Lose fat”.';

  @override
  String get programDisclaimer => 'This program does not replace advice from a health professional.';

  @override
  String get programPreview => 'Preview';

  @override
  String get programCreateButton => 'Create';

  @override
  String get programCreateFailed => 'The program could not be created. Try again.';

  @override
  String programWeekTitle(int week) {
    return 'Week $week';
  }

  @override
  String programWeekHeader(int week, String percent) {
    return 'Week $week · $percent %';
  }

  @override
  String get programDeload => 'Deload';

  @override
  String get programCompleted => 'Completed';

  @override
  String get programNotCompleted => 'Not completed';

  @override
  String get programNotFound => 'This program no longer exists.';

  @override
  String get programWorkoutNotFound => 'This workout is no longer available.';

  @override
  String programDistanceKm(String km) {
    return '$km km';
  }

  @override
  String programSummaryPair(String first, String second) {
    return '$first · $second';
  }

  @override
  String programWeeks(int count) {
    String _temp0 = intl.Intl.pluralLogic(count, locale: localeName, other: '$count weeks', one: '$count week');
    return '$_temp0';
  }

  @override
  String programDaysPerWeek(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count days per week',
      one: '$count day per week',
    );
    return '$_temp0';
  }

  @override
  String programIntervals(int count) {
    String _temp0 = intl.Intl.pluralLogic(count, locale: localeName, other: '$count intervals', one: '$count interval');
    return '$_temp0';
  }

  @override
  String programMinutes(int count) {
    String _temp0 = intl.Intl.pluralLogic(count, locale: localeName, other: '$count min', one: '$count min');
    return '$_temp0';
  }

  @override
  String programRounds(int count) {
    String _temp0 = intl.Intl.pluralLogic(count, locale: localeName, other: '$count rounds', one: '$count round');
    return '$_temp0';
  }

  @override
  String get programOutdoorOnAndroid => 'The outdoor run is available in the Android app.';

  @override
  String get programReset => 'Reset';

  @override
  String get programResetTitle => 'Reset workout?';

  @override
  String get programResetText =>
      'Every exercise will be marked as not done so you can do the workout again. It stays completed in the program.';

  @override
  String get programWorkoutCompletedTitle => 'Workout completed!';

  @override
  String get programWorkoutCompletedText => 'Great job. Your progress has been saved.';

  @override
  String get programSummarySets => 'Sets';

  @override
  String get programSummaryReps => 'Reps';

  @override
  String get programSummaryTime => 'Time';

  @override
  String programSecondsShort(int seconds) {
    return '${seconds}s';
  }

  @override
  String get programPerSide => 'per side';

  @override
  String programExercisesCompleted(int done, int total) {
    String _temp0 = intl.Intl.pluralLogic(
      total,
      locale: localeName,
      other: '$done of $total exercises completed',
      one: '$done of $total exercise completed',
    );
    return '$_temp0';
  }

  @override
  String programExerciseOf(int current, int total) {
    return 'Exercise $current of $total';
  }

  @override
  String programSetOf(int current, int total) {
    return 'Set $current of $total';
  }

  @override
  String get programSetDone => 'Set done';

  @override
  String get programStart => 'Start';

  @override
  String get programGetReady => 'Get ready';

  @override
  String get programRest => 'Rest';

  @override
  String get programSkipRest => 'Skip rest';

  @override
  String programNextSet(int number) {
    return 'Next: set $number';
  }

  @override
  String get programHold => 'Hold the position';

  @override
  String get programLeftSide => 'Left side';

  @override
  String get programRightSide => 'Right side';

  @override
  String get programSwitchSides => 'Switch sides';

  @override
  String get programPerSideOrder => 'Per side: left first, then right';

  @override
  String programRepsPerSide(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count reps per side',
      one: '$count rep per side',
    );
    return '$_temp0';
  }

  @override
  String get programExitExerciseTitle => 'Leave exercise?';

  @override
  String programExitSetsText(int done, int total) {
    String _temp0 = intl.Intl.pluralLogic(
      total,
      locale: localeName,
      other: '$done of $total sets done. This exercise will not be marked as done.',
      one: '$done of $total set done. This exercise will not be marked as done.',
    );
    return '$_temp0';
  }

  @override
  String get programLeave => 'Leave';

  @override
  String get programStay => 'Stay';

  @override
  String get programSegmentWarmUp => 'Warm-up';

  @override
  String get programSegmentJog => 'Jog';

  @override
  String get programSegmentWalk => 'Walk';

  @override
  String get programSegmentInclineWalk => 'Incline walk';

  @override
  String get programSegmentCoolDown => 'Cool-down';

  @override
  String programSegmentOf(int current, int total) {
    return 'Segment $current of $total';
  }

  @override
  String programSegmentLine(String label, String speed, String incline) {
    return '$label · $speed km/h · $incline %';
  }

  @override
  String programSpeed(String speed) {
    return '$speed km/h';
  }

  @override
  String programIncline(String incline) {
    return 'Incline $incline %';
  }

  @override
  String programNext(String label) {
    return 'Next: $label';
  }

  @override
  String get programLastSegment => 'Last segment';

  @override
  String programTotalRemaining(String time) {
    return 'Total remaining: $time';
  }

  @override
  String get programPause => 'Pause';

  @override
  String get programResume => 'Resume';

  @override
  String get programSkipSegment => 'Skip segment';

  @override
  String get programLeaveWorkoutTitle => 'Leave workout?';

  @override
  String get programLeaveWorkoutText => 'This workout will not be marked as completed.';

  @override
  String programRoundOf(int current, int total) {
    return 'Round $current of $total';
  }

  @override
  String get programRoundRest => 'Rest between rounds';

  @override
  String get programWork => 'Work';

  @override
  String programWorkRestSeconds(int work, int rest) {
    return '$work s work / $rest s rest';
  }

  @override
  String get programSkip => 'Skip';

  @override
  String get memberFallbackName => 'Member';

  @override
  String get signOutPendingTitle => 'Changes not backed up';

  @override
  String get signOutPendingText =>
      'Some changes are saved only in this browser and will be lost if you sign out now. Reconnect to send them, or sign out anyway.';

  @override
  String get signOutAnyway => 'Sign out anyway';

  @override
  String get gymTitle => 'Gym routines';

  @override
  String get gymStartRoutine => 'Start routine';

  @override
  String get gymEmptyCatalog => 'No gym routines available';

  @override
  String gymSummary(String sets, String values) {
    return '$sets · $values';
  }

  @override
  String gymSummarySets(int count) {
    return 'Sets: $count';
  }

  @override
  String gymSummaryReps(String values) {
    return 'Reps: $values';
  }

  @override
  String gymSummaryTime(String values) {
    return 'Time: $values';
  }

  @override
  String gymSummarySeconds(int seconds) {
    return '${seconds}s';
  }

  @override
  String get gymStateExpanded => 'Expanded';

  @override
  String get gymStateCollapsed => 'Collapsed';

  @override
  String get gymShowExercises => 'Show exercises';

  @override
  String get gymHideExercises => 'Hide exercises';

  @override
  String get gymStateCompleted => 'Completed';

  @override
  String get gymStateNotCompleted => 'Not completed';

  @override
  String get gymResetProgressTitle => 'Reset progress?';

  @override
  String get gymResetAction => 'Reset progress';

  @override
  String get gymResetText => 'All completion marks of this routine will be cleared. This cannot be undone.';

  @override
  String get gymReset => 'Reset';

  @override
  String gymCompletedCount(int done, int total) {
    String _temp0 = intl.Intl.pluralLogic(
      total,
      locale: localeName,
      other: '$done of $total exercises completed',
      one: '$done of $total exercise completed',
    );
    return '$_temp0';
  }

  @override
  String get gymRoutineEmpty => 'This routine has no exercises yet.';

  @override
  String get gymCompletedTitle => 'Routine completed!';

  @override
  String get gymCompletedText => 'Great job! You finished every exercise of this routine.';

  @override
  String get gymFinish => 'Finish';

  @override
  String get gymRoutineNotFound => 'Routine not found';

  @override
  String gymSetOf(int current, int total) {
    return 'Set $current of $total';
  }

  @override
  String get gymSetDone => 'Set done';

  @override
  String get gymStart => 'Start';

  @override
  String get gymGetReady => 'Get ready';

  @override
  String get gymRest => 'Rest';

  @override
  String gymNextSet(int current, int total) {
    return 'Next: set $current of $total';
  }

  @override
  String get gymSkipRest => 'Skip rest';

  @override
  String get gymExitTitle => 'Leave exercise?';

  @override
  String gymExitText(int done, int total) {
    String _temp0 = intl.Intl.pluralLogic(
      done,
      locale: localeName,
      other:
          'Progress: $done of $total sets completed. If you leave now, this progress will not be saved and the exercise will not be marked as completed.',
      one:
          'Progress: $done of $total set completed. If you leave now, this progress will not be saved and the exercise will not be marked as completed.',
    );
    return '$_temp0';
  }

  @override
  String get gymExitConfirm => 'Leave';

  @override
  String get gymExerciseNotFound => 'Exercise not found';

  @override
  String get gymBrowserNote =>
      'Gym progress stays in this browser: a routine started on another device continues there.';

  @override
  String get trainStartToday => 'Start Today\'s Routine';

  @override
  String trainDayAvailable(String day) {
    return '$day available';
  }

  @override
  String get trainPrograms => 'Programs';

  @override
  String get dailySelectTitle => 'Select Routine';

  @override
  String get dailyAvailableToday => 'Available today';

  @override
  String get dailyStart => 'Start';

  @override
  String dailyLockedHint(String day) {
    return 'Complete $day to unlock';
  }

  @override
  String get dailyStateLocked => 'Locked';

  @override
  String get dailyStateCompleted => 'Completed';

  @override
  String get dailyStateAvailable => 'Available';

  @override
  String get dailyRestartAction => 'Restart days';

  @override
  String get dailyRestartTitle => 'Restart days?';

  @override
  String dailyRestartMessage(String day) {
    return 'Every day will be locked again and you\'ll start from $day. Your history, your weekly goal and your groups won\'t change.';
  }

  @override
  String get dailyRestartConfirm => 'Restart';

  @override
  String get dailyCycleComplete => 'You completed every day!';

  @override
  String dailyStartAgain(String day) {
    return 'Start again from $day';
  }

  @override
  String get photoAdd => 'Add photo';

  @override
  String get photoChange => 'Change photo';

  @override
  String get photoRemove => 'Remove photo';

  @override
  String get photoPrivacyNote =>
      'Photos stay in this browser; your groups only see a small preview. Location data is removed.';

  @override
  String get photoProcessing => 'Saving photo…';

  @override
  String get photoAdded => 'Photo added';

  @override
  String get photoErrorUnreadable => 'This image couldn\'t be read. Try another one.';

  @override
  String get photoErrorHeic =>
      'This browser can\'t read HEIC photos. Choose a JPEG or PNG photo, or set the camera format to Most Compatible.';

  @override
  String photoErrorTooLarge(int maxMb) {
    return 'This photo is too large (maximum $maxMb MB). Choose a smaller one.';
  }

  @override
  String get photoErrorNoSpace => 'Not enough space in this browser to save the photo.';

  @override
  String get photoErrorFailed => 'The photo couldn\'t be saved. Your workout is safe.';

  @override
  String photoContentDescription(String date) {
    return 'Workout photo, $date';
  }

  @override
  String get photoContentDescriptionUndated => 'Workout photo';

  @override
  String get photoOpen => 'Open photo';

  @override
  String get photoClose => 'Close photo';

  @override
  String get photoRemoveTitle => 'Remove the photo?';

  @override
  String get photoRemoveMessage => 'The photo will be deleted from this browser and from your posts in your groups.';

  @override
  String get photoRemoveConfirm => 'Remove';

  @override
  String get photoDropHint => 'You can also drop a photo here.';

  @override
  String get photoOtherDevices => 'Photos added on another device stay on that device.';

  @override
  String get installHintText => 'Add Tio Minuter to your Home Screen so your photos stay saved.';

  @override
  String get installHintDismiss => 'Dismiss';

  @override
  String get signInRedirectIncomplete =>
      'Sign-in didn\'t finish in this browser. Try again; if it keeps happening, allow cookies for this site or use another browser.';

  @override
  String get groupsEmptyText => 'Create a group and share its code, or join one with a code from a friend.';

  @override
  String get groupsCreateButton => 'Create group';

  @override
  String get groupsJoinButton => 'Join with code';

  @override
  String get groupsPrivacyNote =>
      'Groups see your name, photo, weekly count, goal, streak and which kinds of workout you did each day. Never durations, notes or photos.';

  @override
  String groupsMyPosition(int position) {
    return 'This week: #$position';
  }

  @override
  String get groupsGoalRequiredTitle => 'Set your weekly goal first';

  @override
  String get groupsGoalRequiredText =>
      'Groups compare everyone\'s week against their own goal, so you need one before creating or joining a group.';

  @override
  String get groupsGoalRequiredAction => 'Set goal';

  @override
  String get groupsCreateTitle => 'Create a group';

  @override
  String get groupsCreateNameLabel => 'Group name';

  @override
  String get groupsCreateNameError => 'Use 1 to 40 characters.';

  @override
  String get groupsCreateAction => 'Create';

  @override
  String get groupsCreatedTitle => 'Group created!';

  @override
  String groupsCreatedText(String name) {
    return 'Share this code so your friends can join $name.';
  }

  @override
  String get groupsOpenGroup => 'Open group';

  @override
  String get groupsCodeLabel => 'Invite code';

  @override
  String get groupsCopyCode => 'Copy code';

  @override
  String get groupsShareCode => 'Share code';

  @override
  String get groupsCodeCopied => 'Code copied';

  @override
  String groupsShareText(String name, String code, String app) {
    return 'Join my group $name on $app with code $code';
  }

  @override
  String get groupsJoinTitle => 'Join a group';

  @override
  String get groupsJoinCodeLabel => 'Invite code';

  @override
  String get groupsJoinCodeHint => '8 characters, for example K7MPX2QA';

  @override
  String get groupsJoinCodeInvalidChars => 'Codes never use 0, O, 1, I or L.';

  @override
  String get groupsJoinLooking => 'Looking for the group…';

  @override
  String groupsJoinPreview(String name) {
    return 'You\'re about to join $name.';
  }

  @override
  String get groupsJoinAction => 'Join';

  @override
  String get groupsLeave => 'Leave group';

  @override
  String get groupsOwnerMenu => 'Group options';

  @override
  String groupsMemberMenu(String name) {
    return 'Options for $name';
  }

  @override
  String get groupsRename => 'Rename';

  @override
  String get groupsRegenerate => 'New invite code';

  @override
  String get groupsTransfer => 'Transfer ownership';

  @override
  String get groupsDelete => 'Delete group';

  @override
  String get groupsRemove => 'Remove from group';

  @override
  String get groupsMakeOwner => 'Make owner';

  @override
  String get groupsDialogRenameTitle => 'Rename group';

  @override
  String get groupsDialogSave => 'Save';

  @override
  String get groupsDialogRegenerateTitle => 'Create a new invite code?';

  @override
  String get groupsDialogRegenerateText => 'The current code will stop working. People already in the group stay.';

  @override
  String get groupsDialogRegenerateConfirm => 'New code';

  @override
  String groupsDialogRemoveTitle(String name) {
    return 'Remove $name?';
  }

  @override
  String get groupsDialogRemoveText => 'They\'ll leave the group. They can join again with the current code.';

  @override
  String get groupsDialogRemoveConfirm => 'Remove';

  @override
  String get groupsDialogTransferTitle => 'Choose the new owner';

  @override
  String get groupsDialogTransferBeforeLeaving => 'You own this group. Choose who will own it before you leave.';

  @override
  String groupsDialogTransferConfirmTitle(String name) {
    return 'Make $name the owner?';
  }

  @override
  String get groupsDialogTransferConfirmText => 'They\'ll be able to rename, manage members and delete the group.';

  @override
  String get groupsDialogTransferAndLeaveText => 'They\'ll own the group and you\'ll leave it.';

  @override
  String get groupsDialogTransferConfirm => 'Make owner';

  @override
  String get groupsDialogTransferAndLeaveConfirm => 'Transfer and leave';

  @override
  String groupsDialogDeleteTitle(String name) {
    return 'Delete $name?';
  }

  @override
  String get groupsDialogDeleteText => 'Everyone will leave the group and its code will stop working.';

  @override
  String get groupsDialogDeleteNext => 'Continue';

  @override
  String get groupsDialogDeleteConfirmTitle => 'This can\'t be undone';

  @override
  String groupsDialogDeleteConfirmText(String name) {
    return 'Delete $name for every member?';
  }

  @override
  String get groupsDialogDeleteConfirm => 'Delete group';

  @override
  String groupsDialogLeaveTitle(String name) {
    return 'Leave $name?';
  }

  @override
  String get groupsDialogLeaveText => 'You can join again with the group\'s code.';

  @override
  String get groupsDialogLeaveAloneText => 'You\'re the only member, so leaving deletes the group.';

  @override
  String get groupsDialogLeaveConfirm => 'Leave';

  @override
  String get groupsMessageNoNetwork => 'No internet connection. Try again when you\'re online.';

  @override
  String get groupsMessageInvalidCode => 'That code doesn\'t match any group. Check it and try again.';

  @override
  String get groupsMessageGroupFull => 'That group is full (20 members).';

  @override
  String get groupsMessageAlreadyMember => 'You\'re already in that group.';

  @override
  String get groupsMessageGroupLimit => 'You can be in up to 5 groups. Leave one to join another.';

  @override
  String get groupsMessageGoalRequired => 'Set your weekly goal first.';

  @override
  String get groupsMessageInvalidName => 'Use 1 to 40 characters.';

  @override
  String get groupsMessageNotFound => 'This group is no longer available to you.';

  @override
  String get groupsMessageNotAllowed => 'That action isn\'t allowed.';

  @override
  String get groupsMessageFailed => 'Something went wrong. Try again.';

  @override
  String get groupsMessageLeft => 'You left the group.';

  @override
  String get groupsMessageDeleted => 'Group deleted.';

  @override
  String get groupsMessageRenamed => 'Group renamed.';

  @override
  String groupsMessageCodeRegenerated(String code) {
    return 'New code: $code';
  }

  @override
  String groupsMessageMemberRemoved(String name) {
    return '$name was removed.';
  }

  @override
  String groupsMessageOwnerChanged(String name) {
    return '$name is now the owner.';
  }

  @override
  String trainedTodayOne(String name) {
    return '$name already trained today. Your turn 💪';
  }

  @override
  String trainedTodayTwo(String first, String second) {
    return '$first and $second already trained today. Your turn 💪';
  }

  @override
  String get trainedTodayOpen => 'Open groups';

  @override
  String get trainedTodayDismiss => 'Hide for today';

  @override
  String get feedDelete => 'Delete';

  @override
  String get feedHide => 'Hide for me';

  @override
  String feedPostMenu(String name) {
    return 'Options for the workout of $name';
  }

  @override
  String get feedDialogDeleteOwnTitle => 'Delete your post?';

  @override
  String get feedDialogDeleteOwnText => 'It disappears from this group\'s feed. The workout stays in your history.';

  @override
  String feedDialogDeleteOwnerTitle(String name) {
    return 'Delete the post of $name?';
  }

  @override
  String get feedDialogDeleteOwnerText =>
      'It disappears from the group\'s feed for everyone. Their workout stays in their own history.';

  @override
  String get feedMessageHidden => 'Post hidden on this device';

  @override
  String get feedMessageDeleted => 'Post deleted';

  @override
  String get accountDelete => 'Delete account';

  @override
  String get deleteDialogTitle => 'Delete your account?';

  @override
  String get deleteContinue => 'Continue';

  @override
  String get deleteConfirmTitle => 'Delete permanently?';

  @override
  String get authErrorGroupsFailed => 'Couldn\'t leave your groups, so nothing was deleted. Try again.';

  @override
  String get authErrorServerDataFailed =>
      'Couldn\'t delete your backed-up data, so your account wasn\'t deleted. Try again.';

  @override
  String get authErrorAccountNotDeleted =>
      'Your backed-up data was deleted, but your account couldn\'t be deleted. Try Delete account again, or sign out.';

  @override
  String get authErrorDeleteFailed => 'Couldn\'t delete the account. Try again.';

  @override
  String trainedTodayMore(String first, String second, int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$first, $second and $count more already trained today. Your turn 💪',
      one: '$first, $second and $count more already trained today. Your turn 💪',
    );
    return '$_temp0';
  }

  @override
  String get groupsShareCopied => 'Invite copied: paste it in a message to your friends.';

  @override
  String get groupsJoinCodeLength => 'Enter the 8 characters of the code.';

  @override
  String get groupsPostsNote => 'Your posts in a group are deleted when you leave it.';

  @override
  String get deleteDialogMessageWeb =>
      'Your Tio Minuter account and everything backed up online (workouts, goals, programs, your groups and your posts) are deleted. First you\'ll confirm with Google. What this browser keeps for this account (photos, workouts in progress) is removed too.';

  @override
  String get deleteConfirmMessageWeb =>
      'This can\'t be undone. Your account, your cloud data and this browser\'s data for it are deleted.';

  @override
  String get deleteInProgress => 'Deleting your account…';

  @override
  String get accountDeletedWeb => 'Account deleted.';

  @override
  String get deleteWrongAccount => 'Choose the Google account you\'re signed in with, then try again.';
}

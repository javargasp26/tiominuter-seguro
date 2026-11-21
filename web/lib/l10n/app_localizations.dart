import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_en.dart';
import 'app_localizations_es.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of AppLocalizations
/// returned by `AppLocalizations.of(context)`.
///
/// Applications need to include `AppLocalizations.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'l10n/app_localizations.dart';
///
/// return MaterialApp(
///   localizationsDelegates: AppLocalizations.localizationsDelegates,
///   supportedLocales: AppLocalizations.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the AppLocalizations.supportedLocales
/// property.
abstract class AppLocalizations {
  AppLocalizations(String locale) : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static AppLocalizations of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations)!;
  }

  static const LocalizationsDelegate<AppLocalizations> delegate = _AppLocalizationsDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates = <LocalizationsDelegate<dynamic>>[
    delegate,
    GlobalMaterialLocalizations.delegate,
    GlobalCupertinoLocalizations.delegate,
    GlobalWidgetsLocalizations.delegate,
  ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[Locale('en'), Locale('es')];

  /// Android app_name
  ///
  /// In en, this message translates to:
  /// **'Tio Minuter'**
  String get appName;

  /// Android home_screen_tagline, the brand line: the one documented exception to the "no excuses" rule (CLAUDE.md Language guidelines). Don't change it without the product owner's approval
  ///
  /// In en, this message translates to:
  /// **'No excuses — just 10 minutes a day.'**
  String get tagline;

  /// No description provided for @landingTitle.
  ///
  /// In en, this message translates to:
  /// **'Your week, your history and your groups, on the web'**
  String get landingTitle;

  /// No description provided for @landingText.
  ///
  /// In en, this message translates to:
  /// **'Tio Minuter walks you through short daily workouts, gym routines and programs on your Android phone. Sign in with the same Google account to see your weekly goal, your training history and how your groups are doing.'**
  String get landingText;

  /// No description provided for @landingTrainOnPhone.
  ///
  /// In en, this message translates to:
  /// **'Train with the daily routines, your programs and the activities you log, here or in the Android app.'**
  String get landingTrainOnPhone;

  /// Android auth_sign_in_with_google
  ///
  /// In en, this message translates to:
  /// **'Sign in with Google'**
  String get signInWithGoogle;

  /// Android auth_signing_in
  ///
  /// In en, this message translates to:
  /// **'Signing in…'**
  String get signingIn;

  /// Android auth_error_sign_in_failed
  ///
  /// In en, this message translates to:
  /// **'Couldn\'t sign in with Google. Please try again.'**
  String get signInFailed;

  /// Android auth_error_no_network
  ///
  /// In en, this message translates to:
  /// **'No internet connection. Check it and try again.'**
  String get noNetwork;

  /// No description provided for @popupBlocked.
  ///
  /// In en, this message translates to:
  /// **'The sign-in window was blocked. Allow pop-ups for this site, or try again.'**
  String get popupBlocked;

  /// No description provided for @navWeek.
  ///
  /// In en, this message translates to:
  /// **'This week'**
  String get navWeek;

  /// No description provided for @navHistory.
  ///
  /// In en, this message translates to:
  /// **'History'**
  String get navHistory;

  /// No description provided for @navGroups.
  ///
  /// In en, this message translates to:
  /// **'Groups'**
  String get navGroups;

  /// No description provided for @navAccount.
  ///
  /// In en, this message translates to:
  /// **'Account'**
  String get navAccount;

  /// Android weekly_goal_title
  ///
  /// In en, this message translates to:
  /// **'Weekly goal'**
  String get weeklyGoalTitle;

  /// Android weekly_goal_card_no_goal
  ///
  /// In en, this message translates to:
  /// **'Set your weekly goal'**
  String get weekNoGoal;

  /// No description provided for @weekNoGoalHint.
  ///
  /// In en, this message translates to:
  /// **'Set it in the Android app: Home → This week.'**
  String get weekNoGoalHint;

  /// Android weekly_goal_progress
  ///
  /// In en, this message translates to:
  /// **'{done, plural, =1{{done} of {goal} workout} other{{done} of {goal} workouts}}'**
  String weekProgress(int done, int goal);

  /// Android weekly_goal_met
  ///
  /// In en, this message translates to:
  /// **'Goal met this week!'**
  String get weekGoalMet;

  /// Android weekly_goal_workouts_left
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{{count} workout left to reach your goal} other{{count} workouts left to reach your goal}}'**
  String weekWorkoutsLeft(int count);

  /// Android weekly_goal_streak
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{Streak: {count} week} other{Streak: {count} weeks}}'**
  String weekStreak(int count);

  /// Android weekly_goal_explanation
  ///
  /// In en, this message translates to:
  /// **'{minutes, plural, =1{Any workout of at least {minutes} minute counts: daily routines, gym routines, program workouts and the activities you log. Two workouts on the same day count as two.} other{Any workout of at least {minutes} minutes counts: daily routines, gym routines, program workouts and the activities you log. Two workouts on the same day count as two.}}'**
  String weekCountingRule(int minutes);

  /// Android groups_detail_day_none
  ///
  /// In en, this message translates to:
  /// **'{day}: no workouts'**
  String weekDayNone(String day);

  /// Android groups_detail_day_sources
  ///
  /// In en, this message translates to:
  /// **'{day}: {sources}'**
  String weekDaySources(String day, String sources);

  /// No description provided for @weekToday.
  ///
  /// In en, this message translates to:
  /// **'today'**
  String get weekToday;

  /// Android training_history_title
  ///
  /// In en, this message translates to:
  /// **'Training history'**
  String get historyTitle;

  /// Android training_history_empty
  ///
  /// In en, this message translates to:
  /// **'Completed workouts appear here: daily routines, gym routines, program workouts and the activities you log.'**
  String get historyEmpty;

  /// No description provided for @historyToday.
  ///
  /// In en, this message translates to:
  /// **'Today'**
  String get historyToday;

  /// No description provided for @historyYesterday.
  ///
  /// In en, this message translates to:
  /// **'Yesterday'**
  String get historyYesterday;

  /// Android training_history_not_counted
  ///
  /// In en, this message translates to:
  /// **'Doesn\'t count toward the goal'**
  String get historyNotCounted;

  /// No description provided for @historyLoadMore.
  ///
  /// In en, this message translates to:
  /// **'Load more'**
  String get historyLoadMore;

  /// Android training_history_duration_minutes
  ///
  /// In en, this message translates to:
  /// **'{minutes} min'**
  String durationMinutes(int minutes);

  /// No description provided for @sourceDaily.
  ///
  /// In en, this message translates to:
  /// **'Daily routine'**
  String get sourceDaily;

  /// No description provided for @sourceGym.
  ///
  /// In en, this message translates to:
  /// **'Gym routine'**
  String get sourceGym;

  /// No description provided for @sourceProgram.
  ///
  /// In en, this message translates to:
  /// **'Program'**
  String get sourceProgram;

  /// No description provided for @sourceActivity.
  ///
  /// In en, this message translates to:
  /// **'Activity'**
  String get sourceActivity;

  /// Android workout_name_day
  ///
  /// In en, this message translates to:
  /// **'Day {day}'**
  String workoutNameDay(int day);

  /// Android workout_name_day_with_title
  ///
  /// In en, this message translates to:
  /// **'{day} · {title}'**
  String workoutNameDayWithTitle(String day, String title);

  /// No description provided for @programTitleFullBodyA.
  ///
  /// In en, this message translates to:
  /// **'Full body A'**
  String get programTitleFullBodyA;

  /// No description provided for @programTitleFullBodyB.
  ///
  /// In en, this message translates to:
  /// **'Full body B'**
  String get programTitleFullBodyB;

  /// No description provided for @programTitleFullBodyC.
  ///
  /// In en, this message translates to:
  /// **'Full body C'**
  String get programTitleFullBodyC;

  /// No description provided for @programTitleUpperA.
  ///
  /// In en, this message translates to:
  /// **'Upper A'**
  String get programTitleUpperA;

  /// No description provided for @programTitleUpperB.
  ///
  /// In en, this message translates to:
  /// **'Upper B'**
  String get programTitleUpperB;

  /// No description provided for @programTitleLowerA.
  ///
  /// In en, this message translates to:
  /// **'Lower A'**
  String get programTitleLowerA;

  /// No description provided for @programTitleLowerB.
  ///
  /// In en, this message translates to:
  /// **'Lower B'**
  String get programTitleLowerB;

  /// No description provided for @programTitlePush.
  ///
  /// In en, this message translates to:
  /// **'Push'**
  String get programTitlePush;

  /// No description provided for @programTitlePull.
  ///
  /// In en, this message translates to:
  /// **'Pull'**
  String get programTitlePull;

  /// No description provided for @programTitleLegs.
  ///
  /// In en, this message translates to:
  /// **'Legs'**
  String get programTitleLegs;

  /// No description provided for @programTitleFullBody.
  ///
  /// In en, this message translates to:
  /// **'Full body'**
  String get programTitleFullBody;

  /// No description provided for @programTitleUpper.
  ///
  /// In en, this message translates to:
  /// **'Upper'**
  String get programTitleUpper;

  /// No description provided for @programTitleLower.
  ///
  /// In en, this message translates to:
  /// **'Lower'**
  String get programTitleLower;

  /// No description provided for @programTitleUpperC.
  ///
  /// In en, this message translates to:
  /// **'Upper C'**
  String get programTitleUpperC;

  /// No description provided for @programTitleLowerC.
  ///
  /// In en, this message translates to:
  /// **'Lower C'**
  String get programTitleLowerC;

  /// No description provided for @programTitleOutdoorRun.
  ///
  /// In en, this message translates to:
  /// **'Outdoor run'**
  String get programTitleOutdoorRun;

  /// No description provided for @programTitleBriskWalk.
  ///
  /// In en, this message translates to:
  /// **'Brisk walk'**
  String get programTitleBriskWalk;

  /// No description provided for @programTitleTreadmill.
  ///
  /// In en, this message translates to:
  /// **'Treadmill'**
  String get programTitleTreadmill;

  /// No description provided for @programTitleHomeCircuit.
  ///
  /// In en, this message translates to:
  /// **'Home circuit'**
  String get programTitleHomeCircuit;

  /// No description provided for @activityOwnGymRoutine.
  ///
  /// In en, this message translates to:
  /// **'Own gym routine'**
  String get activityOwnGymRoutine;

  /// No description provided for @activityRunning.
  ///
  /// In en, this message translates to:
  /// **'Running'**
  String get activityRunning;

  /// No description provided for @activityWalking.
  ///
  /// In en, this message translates to:
  /// **'Walking'**
  String get activityWalking;

  /// No description provided for @activityCycling.
  ///
  /// In en, this message translates to:
  /// **'Cycling'**
  String get activityCycling;

  /// No description provided for @activitySwimming.
  ///
  /// In en, this message translates to:
  /// **'Swimming'**
  String get activitySwimming;

  /// No description provided for @activityYoga.
  ///
  /// In en, this message translates to:
  /// **'Yoga'**
  String get activityYoga;

  /// No description provided for @activityPilates.
  ///
  /// In en, this message translates to:
  /// **'Pilates'**
  String get activityPilates;

  /// No description provided for @activityDance.
  ///
  /// In en, this message translates to:
  /// **'Dance'**
  String get activityDance;

  /// No description provided for @activityTeamSport.
  ///
  /// In en, this message translates to:
  /// **'Team sport'**
  String get activityTeamSport;

  /// No description provided for @activityRacketSport.
  ///
  /// In en, this message translates to:
  /// **'Racket sport'**
  String get activityRacketSport;

  /// No description provided for @activityMartialArts.
  ///
  /// In en, this message translates to:
  /// **'Martial arts'**
  String get activityMartialArts;

  /// No description provided for @activityHiit.
  ///
  /// In en, this message translates to:
  /// **'HIIT'**
  String get activityHiit;

  /// No description provided for @activityStretching.
  ///
  /// In en, this message translates to:
  /// **'Stretching'**
  String get activityStretching;

  /// No description provided for @activityOther.
  ///
  /// In en, this message translates to:
  /// **'Other'**
  String get activityOther;

  /// No description provided for @groupsTitle.
  ///
  /// In en, this message translates to:
  /// **'Groups'**
  String get groupsTitle;

  /// Android groups_empty_title
  ///
  /// In en, this message translates to:
  /// **'No groups yet'**
  String get groupsEmptyTitle;

  /// Android groups_owner_label
  ///
  /// In en, this message translates to:
  /// **'Owner'**
  String get groupsOwner;

  /// Android groups_member_count
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{{count} member} other{{count} members}}'**
  String groupsMemberCount(int count);

  /// No description provided for @groupsTabRanking.
  ///
  /// In en, this message translates to:
  /// **'Ranking'**
  String get groupsTabRanking;

  /// No description provided for @groupsTabFeed.
  ///
  /// In en, this message translates to:
  /// **'Feed'**
  String get groupsTabFeed;

  /// No description provided for @groupsThisWeek.
  ///
  /// In en, this message translates to:
  /// **'This week'**
  String get groupsThisWeek;

  /// Android groups_detail_me
  ///
  /// In en, this message translates to:
  /// **'You'**
  String get groupsMe;

  /// Android groups_detail_progress
  ///
  /// In en, this message translates to:
  /// **'{done}/{goal}'**
  String groupsProgress(int done, int goal);

  /// No description provided for @groupsStreak.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{Streak: {count} week} other{Streak: {count} weeks}}'**
  String groupsStreak(int count);

  /// No description provided for @groupsPosition.
  ///
  /// In en, this message translates to:
  /// **'#{position}'**
  String groupsPosition(int position);

  /// Android groups_detail_not_updated
  ///
  /// In en, this message translates to:
  /// **'Not updated this week'**
  String get groupsNotUpdated;

  /// No description provided for @groupsGone.
  ///
  /// In en, this message translates to:
  /// **'This group is no longer available.'**
  String get groupsGone;

  /// Android feed_empty
  ///
  /// In en, this message translates to:
  /// **'No workouts shared this week yet.'**
  String get feedEmpty;

  /// Android feed_empty_monday
  ///
  /// In en, this message translates to:
  /// **'A new week starts today. Workouts your group finishes will show up here.'**
  String get feedEmptyMonday;

  /// No description provided for @feedLoadMore.
  ///
  /// In en, this message translates to:
  /// **'Load more'**
  String get feedLoadMore;

  /// No description provided for @feedPhotoDescription.
  ///
  /// In en, this message translates to:
  /// **'Workout photo of {name}'**
  String feedPhotoDescription(String name);

  /// Android feed_reaction_description
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{{label}, {count} reaction} other{{label}, {count} reactions}}'**
  String feedReactionDescription(String label, int count);

  /// No description provided for @feedReactionMine.
  ///
  /// In en, this message translates to:
  /// **'Your reaction'**
  String get feedReactionMine;

  /// No description provided for @feedReactionNotMine.
  ///
  /// In en, this message translates to:
  /// **'Not selected'**
  String get feedReactionNotMine;

  /// No description provided for @feedReactionStrong.
  ///
  /// In en, this message translates to:
  /// **'Strong'**
  String get feedReactionStrong;

  /// No description provided for @feedReactionFire.
  ///
  /// In en, this message translates to:
  /// **'On fire'**
  String get feedReactionFire;

  /// No description provided for @feedReactionClap.
  ///
  /// In en, this message translates to:
  /// **'Applause'**
  String get feedReactionClap;

  /// No description provided for @feedReactionRaised.
  ///
  /// In en, this message translates to:
  /// **'Celebrate'**
  String get feedReactionRaised;

  /// No description provided for @feedJustNow.
  ///
  /// In en, this message translates to:
  /// **'Just now'**
  String get feedJustNow;

  /// Android feed_message_reaction_failed
  ///
  /// In en, this message translates to:
  /// **'Couldn\'t save your reaction'**
  String get feedReactionFailed;

  /// No description provided for @accountTitle.
  ///
  /// In en, this message translates to:
  /// **'Account'**
  String get accountTitle;

  /// No description provided for @accountPhotoDescription.
  ///
  /// In en, this message translates to:
  /// **'Profile photo'**
  String get accountPhotoDescription;

  /// No description provided for @accountSignOut.
  ///
  /// In en, this message translates to:
  /// **'Sign out'**
  String get accountSignOut;

  /// No description provided for @accountSignedOut.
  ///
  /// In en, this message translates to:
  /// **'Signed out.'**
  String get accountSignedOut;

  /// No description provided for @loadFailed.
  ///
  /// In en, this message translates to:
  /// **'Couldn\'t load this. Check your connection and try again.'**
  String get loadFailed;

  /// No description provided for @retry.
  ///
  /// In en, this message translates to:
  /// **'Retry'**
  String get retry;

  /// No description provided for @loading.
  ///
  /// In en, this message translates to:
  /// **'Loading…'**
  String get loading;

  /// Web only: the training section of the navigation
  ///
  /// In en, this message translates to:
  /// **'Train'**
  String get navTrain;

  /// Web only: title of the training hub
  ///
  /// In en, this message translates to:
  /// **'Train'**
  String get trainTitle;

  /// Android program_exercises_count
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{{count} exercise} other{{count} exercises}}'**
  String trainExercisesCount(int count);

  /// Android no_routines_available
  ///
  /// In en, this message translates to:
  /// **'No routines available'**
  String get trainNoRoutines;

  /// Web only: a workout kept after a reload
  ///
  /// In en, this message translates to:
  /// **'Workout in progress: {name}'**
  String trainInProgress(String name);

  /// Web only: resume the workout in progress
  ///
  /// In en, this message translates to:
  /// **'Continue'**
  String get trainContinue;

  /// Android gym_session_saved
  ///
  /// In en, this message translates to:
  /// **'Your unfinished workout was saved ({duration}).'**
  String sessionSaved(String duration);

  /// Android gym_session_duration_hours_minutes (minutes with two digits)
  ///
  /// In en, this message translates to:
  /// **'{hours} h {minutes} min'**
  String sessionDurationHoursMinutes(int hours, String minutes);

  /// Android gym_session_duration_minutes
  ///
  /// In en, this message translates to:
  /// **'{minutes} min'**
  String sessionDurationMinutes(int minutes);

  /// Android back
  ///
  /// In en, this message translates to:
  /// **'Back'**
  String get back;

  /// Web only: close a finished workout
  ///
  /// In en, this message translates to:
  /// **'Done'**
  String get done;

  /// Android program_cancel
  ///
  /// In en, this message translates to:
  /// **'Cancel'**
  String get cancel;

  /// Web only: writes waiting for the connection
  ///
  /// In en, this message translates to:
  /// **'Not synced yet'**
  String get pendingWrites;

  /// Web only: what the pending indicator means
  ///
  /// In en, this message translates to:
  /// **'Your changes are saved in this browser and will be sent when the connection returns.'**
  String get pendingWritesDescription;

  /// Android workout
  ///
  /// In en, this message translates to:
  /// **'Workout'**
  String get workoutTitle;

  /// Android exercise_progress
  ///
  /// In en, this message translates to:
  /// **'Exercise {current} of {total}'**
  String workoutExerciseProgress(int current, int total);

  /// Android get_ready
  ///
  /// In en, this message translates to:
  /// **'Get Ready'**
  String get workoutGetReady;

  /// Android get_ready_banner
  ///
  /// In en, this message translates to:
  /// **'GET READY'**
  String get workoutGetReadyBanner;

  /// Android skip_exercise
  ///
  /// In en, this message translates to:
  /// **'Skip Exercise'**
  String get workoutSkipExercise;

  /// Android next_exercise
  ///
  /// In en, this message translates to:
  /// **'Next Exercise'**
  String get workoutNextExercise;

  /// Android complete_reps
  ///
  /// In en, this message translates to:
  /// **'Complete Reps'**
  String get workoutCompleteReps;

  /// Android timer_running
  ///
  /// In en, this message translates to:
  /// **'Timer running…'**
  String get workoutTimerRunning;

  /// Android rest
  ///
  /// In en, this message translates to:
  /// **'Rest'**
  String get workoutRest;

  /// Android seconds
  ///
  /// In en, this message translates to:
  /// **'seconds'**
  String get workoutSeconds;

  /// Android program_reps
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{{count} rep} other{{count} reps}}'**
  String workoutReps(int count);

  /// Android workout_complete
  ///
  /// In en, this message translates to:
  /// **'Workout Complete!'**
  String get workoutComplete;

  /// Android great_job
  ///
  /// In en, this message translates to:
  /// **'Great job!'**
  String get workoutGreatJob;

  /// Web only: the icon shown instead of the animation
  ///
  /// In en, this message translates to:
  /// **'Timed exercise'**
  String get exerciseTypeTime;

  /// Web only: the icon shown instead of the animation
  ///
  /// In en, this message translates to:
  /// **'Repetition exercise'**
  String get exerciseTypeReps;

  /// Web only: the icon shown instead of the animation
  ///
  /// In en, this message translates to:
  /// **'Rest'**
  String get exerciseTypeRest;

  /// Android program_unknown_exercise
  ///
  /// In en, this message translates to:
  /// **'Unknown exercise'**
  String get unknownExercise;

  /// Android share_in_groups_label
  ///
  /// In en, this message translates to:
  /// **'Share in my groups'**
  String get shareInGroups;

  /// Android share_in_groups_on
  ///
  /// In en, this message translates to:
  /// **'Your groups will see this workout in their feed.'**
  String get shareInGroupsOn;

  /// Android share_in_groups_off
  ///
  /// In en, this message translates to:
  /// **'This workout stays private.'**
  String get shareInGroupsOff;

  /// Android share_intro_title
  ///
  /// In en, this message translates to:
  /// **'Share your workouts with your groups'**
  String get shareIntroTitle;

  /// Android share_intro_text, without photos (the web posts no thumbnail)
  ///
  /// In en, this message translates to:
  /// **'When you finish a workout, the members of your groups see it in the group\'s feed: your name and photo, the workout\'s title and how long it took. Notes and intensity are never shared. You can turn sharing off for any workout.'**
  String get shareIntroText;

  /// Android share_intro_ok
  ///
  /// In en, this message translates to:
  /// **'OK'**
  String get shareIntroOk;

  /// Android share_intro_dont_share
  ///
  /// In en, this message translates to:
  /// **'Don\'t share this one'**
  String get shareIntroDontShare;

  /// Android log_activity_button
  ///
  /// In en, this message translates to:
  /// **'Log activity'**
  String get logActivity;

  /// Android log_activity_title_new
  ///
  /// In en, this message translates to:
  /// **'Log an activity'**
  String get logActivityTitleNew;

  /// Android log_activity_title_edit
  ///
  /// In en, this message translates to:
  /// **'Edit activity'**
  String get logActivityTitleEdit;

  /// Android log_activity_type
  ///
  /// In en, this message translates to:
  /// **'Activity'**
  String get logActivityType;

  /// Android log_activity_custom_name
  ///
  /// In en, this message translates to:
  /// **'Activity name'**
  String get logActivityCustomName;

  /// Android log_activity_counter
  ///
  /// In en, this message translates to:
  /// **'{count}/{max}'**
  String logActivityCounter(int count, int max);

  /// Android log_activity_duration
  ///
  /// In en, this message translates to:
  /// **'Duration'**
  String get logActivityDuration;

  /// Android log_activity_minutes_short
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{{count} min} other{{count} min}}'**
  String logActivityMinutesShort(int count);

  /// Android log_activity_minutes_field
  ///
  /// In en, this message translates to:
  /// **'Minutes'**
  String get logActivityMinutesField;

  /// Android log_activity_short_duration_hint
  ///
  /// In en, this message translates to:
  /// **'{minutes, plural, =1{It won\'t count toward your weekly goal (minimum {minutes} minute)} other{It won\'t count toward your weekly goal (minimum {minutes} minutes)}}'**
  String logActivityShortDurationHint(int minutes);

  /// Android log_activity_intensity
  ///
  /// In en, this message translates to:
  /// **'Intensity'**
  String get logActivityIntensity;

  /// Android intensity_easy
  ///
  /// In en, this message translates to:
  /// **'Easy'**
  String get intensityEasy;

  /// Android intensity_moderate
  ///
  /// In en, this message translates to:
  /// **'Moderate'**
  String get intensityModerate;

  /// Android intensity_hard
  ///
  /// In en, this message translates to:
  /// **'Hard'**
  String get intensityHard;

  /// Android intensity_easy_description
  ///
  /// In en, this message translates to:
  /// **'You can talk normally'**
  String get intensityEasyDescription;

  /// Android intensity_moderate_description
  ///
  /// In en, this message translates to:
  /// **'You can talk with effort'**
  String get intensityModerateDescription;

  /// Android intensity_hard_description
  ///
  /// In en, this message translates to:
  /// **'Hard to talk'**
  String get intensityHardDescription;

  /// Android log_activity_intensity_option
  ///
  /// In en, this message translates to:
  /// **'{label}: {description}'**
  String logActivityIntensityOption(String label, String description);

  /// Android log_activity_when
  ///
  /// In en, this message translates to:
  /// **'When'**
  String get logActivityWhen;

  /// Android log_activity_time
  ///
  /// In en, this message translates to:
  /// **'Time: {time}'**
  String logActivityTime(String time);

  /// Android log_activity_time_change
  ///
  /// In en, this message translates to:
  /// **'Time: {time}. Change the time'**
  String logActivityTimeChange(String time);

  /// Android log_activity_note
  ///
  /// In en, this message translates to:
  /// **'Note (optional)'**
  String get logActivityNote;

  /// Android log_activity_save
  ///
  /// In en, this message translates to:
  /// **'Save'**
  String get logActivitySave;

  /// Android log_activity_delete
  ///
  /// In en, this message translates to:
  /// **'Delete activity'**
  String get logActivityDelete;

  /// Android log_activity_delete_title
  ///
  /// In en, this message translates to:
  /// **'Delete this activity?'**
  String get logActivityDeleteTitle;

  /// Android log_activity_delete_message
  ///
  /// In en, this message translates to:
  /// **'It will be removed from the history and the weekly goal.'**
  String get logActivityDeleteMessage;

  /// Android log_activity_delete_confirm
  ///
  /// In en, this message translates to:
  /// **'Delete'**
  String get logActivityDeleteConfirm;

  /// Android log_activity_discard_title
  ///
  /// In en, this message translates to:
  /// **'Discard changes?'**
  String get logActivityDiscardTitle;

  /// Android log_activity_discard_message
  ///
  /// In en, this message translates to:
  /// **'The changes you made will be lost.'**
  String get logActivityDiscardMessage;

  /// Android log_activity_discard_confirm
  ///
  /// In en, this message translates to:
  /// **'Discard'**
  String get logActivityDiscardConfirm;

  /// Android log_activity_keep_editing
  ///
  /// In en, this message translates to:
  /// **'Keep editing'**
  String get logActivityKeepEditing;

  /// Android log_activity_error_type
  ///
  /// In en, this message translates to:
  /// **'Choose an activity'**
  String get logActivityErrorType;

  /// Android log_activity_error_name
  ///
  /// In en, this message translates to:
  /// **'Write the name of the activity'**
  String get logActivityErrorName;

  /// Android log_activity_error_too_long
  ///
  /// In en, this message translates to:
  /// **'{max, plural, =1{Use at most {max} character} other{Use at most {max} characters}}'**
  String logActivityErrorTooLong(int max);

  /// Android log_activity_error_duration
  ///
  /// In en, this message translates to:
  /// **'{max, plural, =1{Enter between {min} and {max} minute} other{Enter between {min} and {max} minutes}}'**
  String logActivityErrorDuration(int min, int max);

  /// Android log_activity_error_intensity
  ///
  /// In en, this message translates to:
  /// **'Choose an intensity'**
  String get logActivityErrorIntensity;

  /// Android log_activity_error_future
  ///
  /// In en, this message translates to:
  /// **'The time can\'t be in the future'**
  String get logActivityErrorFuture;

  /// Android log_activity_error_too_early
  ///
  /// In en, this message translates to:
  /// **'{days, plural, =1{Choose a day within the last {days} day} other{Choose a day within the last {days} days}}'**
  String logActivityErrorTooEarly(int days);

  /// Android log_activity_save_failed
  ///
  /// In en, this message translates to:
  /// **'The activity could not be saved. Try again.'**
  String get logActivitySaveFailed;

  /// Android log_activity_not_found
  ///
  /// In en, this message translates to:
  /// **'This activity no longer exists.'**
  String get logActivityNotFound;

  /// Android weekly_goal_question
  ///
  /// In en, this message translates to:
  /// **'How many workouts a week do you want to do?'**
  String get weeklyGoalQuestion;

  /// Android weekly_goal_workouts_per_week
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{{count} workout per week} other{{count} workouts per week}}'**
  String weeklyGoalWorkoutsPerWeek(int count);

  /// Android weekly_goal_fewer
  ///
  /// In en, this message translates to:
  /// **'Fewer workouts'**
  String get weeklyGoalFewer;

  /// Android weekly_goal_more
  ///
  /// In en, this message translates to:
  /// **'More workouts'**
  String get weeklyGoalMore;

  /// Android weekly_goal_applies_from_this_week
  ///
  /// In en, this message translates to:
  /// **'The change applies from this week; past weeks keep their goal.'**
  String get weeklyGoalAppliesFromThisWeek;

  /// Android weekly_goal_save
  ///
  /// In en, this message translates to:
  /// **'Save'**
  String get weeklyGoalSave;

  /// Android weekly_goal_card_set_goal
  ///
  /// In en, this message translates to:
  /// **'Set goal'**
  String get weeklyGoalSet;

  /// Web only: open the goal from My week
  ///
  /// In en, this message translates to:
  /// **'Change goal'**
  String get weeklyGoalChange;

  /// Android training_history_delete_action
  ///
  /// In en, this message translates to:
  /// **'Delete entry'**
  String get historyDeleteAction;

  /// Android training_history_delete_title
  ///
  /// In en, this message translates to:
  /// **'Delete this entry?'**
  String get historyDeleteTitle;

  /// Android training_history_delete_message
  ///
  /// In en, this message translates to:
  /// **'\"{name}\" will be removed from the history.'**
  String historyDeleteMessage(String name);

  /// Android training_history_delete_confirm
  ///
  /// In en, this message translates to:
  /// **'Delete'**
  String get historyDeleteConfirm;

  /// Android log_activity_title_edit
  ///
  /// In en, this message translates to:
  /// **'Edit activity'**
  String get historyEditActivity;

  /// Android program_list_title
  ///
  /// In en, this message translates to:
  /// **'My programs'**
  String get programsTitle;

  /// Android program_list_empty_title
  ///
  /// In en, this message translates to:
  /// **'No programs yet'**
  String get programsEmptyTitle;

  /// Android program_list_empty_text, without the + button
  ///
  /// In en, this message translates to:
  /// **'Create a training plan of several weeks based on your age, weight, level and goal.'**
  String get programsEmptyText;

  /// Android program_create_content_description
  ///
  /// In en, this message translates to:
  /// **'Create program'**
  String get programCreate;

  /// Android program_goal_gain_muscle
  ///
  /// In en, this message translates to:
  /// **'Gain strength and muscle'**
  String get programGoalGainMuscle;

  /// Android program_goal_lose_fat
  ///
  /// In en, this message translates to:
  /// **'Lose fat'**
  String get programGoalLoseFat;

  /// Android program_level_beginner
  ///
  /// In en, this message translates to:
  /// **'Beginner'**
  String get programLevelBeginner;

  /// Android program_level_intermediate
  ///
  /// In en, this message translates to:
  /// **'Intermediate'**
  String get programLevelIntermediate;

  /// Android program_level_advanced
  ///
  /// In en, this message translates to:
  /// **'Advanced'**
  String get programLevelAdvanced;

  /// Android program_location_home
  ///
  /// In en, this message translates to:
  /// **'Home'**
  String get programLocationHome;

  /// Android program_location_gym
  ///
  /// In en, this message translates to:
  /// **'Gym'**
  String get programLocationGym;

  /// Android program_card_meta
  ///
  /// In en, this message translates to:
  /// **'{first} · {second}'**
  String programCardMeta(String first, String second);

  /// Android program_progress
  ///
  /// In en, this message translates to:
  /// **'Progress: {percent} %'**
  String programProgress(String percent);

  /// Android program_create_title
  ///
  /// In en, this message translates to:
  /// **'New program'**
  String get programCreateTitle;

  /// Android program_age_label
  ///
  /// In en, this message translates to:
  /// **'Age'**
  String get programAgeLabel;

  /// Android program_weight_label
  ///
  /// In en, this message translates to:
  /// **'Weight (kg)'**
  String get programWeightLabel;

  /// Android program_height_label
  ///
  /// In en, this message translates to:
  /// **'Height (cm)'**
  String get programHeightLabel;

  /// Android program_error_required
  ///
  /// In en, this message translates to:
  /// **'Required'**
  String get programErrorRequired;

  /// Android program_error_range
  ///
  /// In en, this message translates to:
  /// **'Enter a value from {min} to {max}'**
  String programErrorRange(int min, int max);

  /// Android program_hint_range
  ///
  /// In en, this message translates to:
  /// **'From {min} to {max}'**
  String programHintRange(int min, int max);

  /// Android program_level_label
  ///
  /// In en, this message translates to:
  /// **'Level'**
  String get programLevelLabel;

  /// Android program_goal_label
  ///
  /// In en, this message translates to:
  /// **'Goal'**
  String get programGoalLabel;

  /// Android program_location_label
  ///
  /// In en, this message translates to:
  /// **'Where you train'**
  String get programLocationLabel;

  /// Android program_focus_label
  ///
  /// In en, this message translates to:
  /// **'Focus'**
  String get programFocusLabel;

  /// Android program_focus_balanced
  ///
  /// In en, this message translates to:
  /// **'Balanced'**
  String get programFocusBalanced;

  /// Android program_focus_balanced_description
  ///
  /// In en, this message translates to:
  /// **'The whole body, evenly'**
  String get programFocusBalancedDescription;

  /// Android program_focus_lower_body
  ///
  /// In en, this message translates to:
  /// **'Lower body'**
  String get programFocusLowerBody;

  /// Android program_focus_lower_body_description
  ///
  /// In en, this message translates to:
  /// **'More glutes and legs'**
  String get programFocusLowerBodyDescription;

  /// Android program_focus_upper_body
  ///
  /// In en, this message translates to:
  /// **'Upper body'**
  String get programFocusUpperBody;

  /// Android program_focus_upper_body_description
  ///
  /// In en, this message translates to:
  /// **'More chest, back, shoulders and arms'**
  String get programFocusUpperBodyDescription;

  /// Android program_focus_core
  ///
  /// In en, this message translates to:
  /// **'Core and stability'**
  String get programFocusCore;

  /// Android program_focus_core_description
  ///
  /// In en, this message translates to:
  /// **'More core and balance work'**
  String get programFocusCoreDescription;

  /// Android program_focus_value
  ///
  /// In en, this message translates to:
  /// **'Focus: {focus}'**
  String programFocusValue(String focus);

  /// Android program_weeks_label
  ///
  /// In en, this message translates to:
  /// **'Weeks'**
  String get programWeeksLabel;

  /// Android program_days_label
  ///
  /// In en, this message translates to:
  /// **'Days per week'**
  String get programDaysLabel;

  /// Android program_decrease
  ///
  /// In en, this message translates to:
  /// **'Decrease {label}'**
  String programDecrease(String label);

  /// Android program_increase
  ///
  /// In en, this message translates to:
  /// **'Increase {label}'**
  String programIncrease(String label);

  /// Android program_bmi
  ///
  /// In en, this message translates to:
  /// **'BMI: {value}'**
  String programBmi(String value);

  /// Android program_low_impact_note
  ///
  /// In en, this message translates to:
  /// **'Your plan uses low-impact exercises: no jumping or running, and walking instead of running.'**
  String get programLowImpactNote;

  /// Android program_underweight_warning
  ///
  /// In en, this message translates to:
  /// **'Your BMI is below 18.5. Consider choosing “Gain strength and muscle” instead of “Lose fat”.'**
  String get programUnderweightWarning;

  /// Android program_disclaimer
  ///
  /// In en, this message translates to:
  /// **'This program does not replace advice from a health professional.'**
  String get programDisclaimer;

  /// Android program_preview_button
  ///
  /// In en, this message translates to:
  /// **'Preview'**
  String get programPreview;

  /// Android program_create_button
  ///
  /// In en, this message translates to:
  /// **'Create'**
  String get programCreateButton;

  /// Android program_create_failed
  ///
  /// In en, this message translates to:
  /// **'The program could not be created. Try again.'**
  String get programCreateFailed;

  /// Android program_week_title
  ///
  /// In en, this message translates to:
  /// **'Week {week}'**
  String programWeekTitle(int week);

  /// Android program_week_header
  ///
  /// In en, this message translates to:
  /// **'Week {week} · {percent} %'**
  String programWeekHeader(int week, String percent);

  /// Android program_deload
  ///
  /// In en, this message translates to:
  /// **'Deload'**
  String get programDeload;

  /// Android program_completed
  ///
  /// In en, this message translates to:
  /// **'Completed'**
  String get programCompleted;

  /// Android program_not_completed
  ///
  /// In en, this message translates to:
  /// **'Not completed'**
  String get programNotCompleted;

  /// Android program_program_not_found
  ///
  /// In en, this message translates to:
  /// **'This program no longer exists.'**
  String get programNotFound;

  /// Android program_not_found
  ///
  /// In en, this message translates to:
  /// **'This workout is no longer available.'**
  String get programWorkoutNotFound;

  /// Android program_distance_km
  ///
  /// In en, this message translates to:
  /// **'{km} km'**
  String programDistanceKm(String km);

  /// Android program_treadmill_summary / program_circuit_summary
  ///
  /// In en, this message translates to:
  /// **'{first} · {second}'**
  String programSummaryPair(String first, String second);

  /// Android program_weeks
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{{count} week} other{{count} weeks}}'**
  String programWeeks(int count);

  /// Android program_days_per_week
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{{count} day per week} other{{count} days per week}}'**
  String programDaysPerWeek(int count);

  /// Android program_intervals
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{{count} interval} other{{count} intervals}}'**
  String programIntervals(int count);

  /// Android program_minutes
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{{count} min} other{{count} min}}'**
  String programMinutes(int count);

  /// Android program_rounds
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{{count} round} other{{count} rounds}}'**
  String programRounds(int count);

  /// Web only: the outdoor run is not on the web
  ///
  /// In en, this message translates to:
  /// **'The outdoor run is available in the Android app.'**
  String get programOutdoorOnAndroid;

  /// Android program_reset
  ///
  /// In en, this message translates to:
  /// **'Reset'**
  String get programReset;

  /// Android program_reset_title
  ///
  /// In en, this message translates to:
  /// **'Reset workout?'**
  String get programResetTitle;

  /// Android program_reset_text
  ///
  /// In en, this message translates to:
  /// **'Every exercise will be marked as not done so you can do the workout again. It stays completed in the program.'**
  String get programResetText;

  /// Android program_workout_completed_title
  ///
  /// In en, this message translates to:
  /// **'Workout completed!'**
  String get programWorkoutCompletedTitle;

  /// Android program_workout_completed_text
  ///
  /// In en, this message translates to:
  /// **'Great job. Your progress has been saved.'**
  String get programWorkoutCompletedText;

  /// Android program_summary_sets
  ///
  /// In en, this message translates to:
  /// **'Sets'**
  String get programSummarySets;

  /// Android program_summary_reps
  ///
  /// In en, this message translates to:
  /// **'Reps'**
  String get programSummaryReps;

  /// Android program_summary_time
  ///
  /// In en, this message translates to:
  /// **'Time'**
  String get programSummaryTime;

  /// Android program_seconds_short
  ///
  /// In en, this message translates to:
  /// **'{seconds}s'**
  String programSecondsShort(int seconds);

  /// Android program_per_side
  ///
  /// In en, this message translates to:
  /// **'per side'**
  String get programPerSide;

  /// Android program_exercises_completed
  ///
  /// In en, this message translates to:
  /// **'{total, plural, =1{{done} of {total} exercise completed} other{{done} of {total} exercises completed}}'**
  String programExercisesCompleted(int done, int total);

  /// Android program_exercise_of
  ///
  /// In en, this message translates to:
  /// **'Exercise {current} of {total}'**
  String programExerciseOf(int current, int total);

  /// Android program_set_of
  ///
  /// In en, this message translates to:
  /// **'Set {current} of {total}'**
  String programSetOf(int current, int total);

  /// Android program_set_done
  ///
  /// In en, this message translates to:
  /// **'Set done'**
  String get programSetDone;

  /// Android program_start
  ///
  /// In en, this message translates to:
  /// **'Start'**
  String get programStart;

  /// Android program_get_ready
  ///
  /// In en, this message translates to:
  /// **'Get ready'**
  String get programGetReady;

  /// Android program_rest
  ///
  /// In en, this message translates to:
  /// **'Rest'**
  String get programRest;

  /// Android program_skip_rest
  ///
  /// In en, this message translates to:
  /// **'Skip rest'**
  String get programSkipRest;

  /// Android program_next_set
  ///
  /// In en, this message translates to:
  /// **'Next: set {number}'**
  String programNextSet(int number);

  /// Android program_hold
  ///
  /// In en, this message translates to:
  /// **'Hold the position'**
  String get programHold;

  /// Android program_left_side
  ///
  /// In en, this message translates to:
  /// **'Left side'**
  String get programLeftSide;

  /// Android program_right_side
  ///
  /// In en, this message translates to:
  /// **'Right side'**
  String get programRightSide;

  /// Android program_switch_sides
  ///
  /// In en, this message translates to:
  /// **'Switch sides'**
  String get programSwitchSides;

  /// Android program_per_side_order
  ///
  /// In en, this message translates to:
  /// **'Per side: left first, then right'**
  String get programPerSideOrder;

  /// Android program_reps_per_side
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{{count} rep per side} other{{count} reps per side}}'**
  String programRepsPerSide(int count);

  /// Android program_exit_exercise_title
  ///
  /// In en, this message translates to:
  /// **'Leave exercise?'**
  String get programExitExerciseTitle;

  /// Android program_exit_sets_text
  ///
  /// In en, this message translates to:
  /// **'{total, plural, =1{{done} of {total} set done. This exercise will not be marked as done.} other{{done} of {total} sets done. This exercise will not be marked as done.}}'**
  String programExitSetsText(int done, int total);

  /// Android program_leave
  ///
  /// In en, this message translates to:
  /// **'Leave'**
  String get programLeave;

  /// Android program_stay
  ///
  /// In en, this message translates to:
  /// **'Stay'**
  String get programStay;

  /// Android program_segment_warm_up
  ///
  /// In en, this message translates to:
  /// **'Warm-up'**
  String get programSegmentWarmUp;

  /// Android program_segment_jog
  ///
  /// In en, this message translates to:
  /// **'Jog'**
  String get programSegmentJog;

  /// Android program_segment_walk
  ///
  /// In en, this message translates to:
  /// **'Walk'**
  String get programSegmentWalk;

  /// Android program_segment_incline_walk
  ///
  /// In en, this message translates to:
  /// **'Incline walk'**
  String get programSegmentInclineWalk;

  /// Android program_segment_cool_down
  ///
  /// In en, this message translates to:
  /// **'Cool-down'**
  String get programSegmentCoolDown;

  /// Android program_segment_of
  ///
  /// In en, this message translates to:
  /// **'Segment {current} of {total}'**
  String programSegmentOf(int current, int total);

  /// Android program_segment_line
  ///
  /// In en, this message translates to:
  /// **'{label} · {speed} km/h · {incline} %'**
  String programSegmentLine(String label, String speed, String incline);

  /// Android program_speed
  ///
  /// In en, this message translates to:
  /// **'{speed} km/h'**
  String programSpeed(String speed);

  /// Android program_incline
  ///
  /// In en, this message translates to:
  /// **'Incline {incline} %'**
  String programIncline(String incline);

  /// Android program_next
  ///
  /// In en, this message translates to:
  /// **'Next: {label}'**
  String programNext(String label);

  /// Android program_last_segment
  ///
  /// In en, this message translates to:
  /// **'Last segment'**
  String get programLastSegment;

  /// Android program_total_remaining
  ///
  /// In en, this message translates to:
  /// **'Total remaining: {time}'**
  String programTotalRemaining(String time);

  /// Android program_pause
  ///
  /// In en, this message translates to:
  /// **'Pause'**
  String get programPause;

  /// Android program_resume
  ///
  /// In en, this message translates to:
  /// **'Resume'**
  String get programResume;

  /// Android program_skip_segment
  ///
  /// In en, this message translates to:
  /// **'Skip segment'**
  String get programSkipSegment;

  /// Android program_leave_workout_title
  ///
  /// In en, this message translates to:
  /// **'Leave workout?'**
  String get programLeaveWorkoutTitle;

  /// Android program_leave_workout_text
  ///
  /// In en, this message translates to:
  /// **'This workout will not be marked as completed.'**
  String get programLeaveWorkoutText;

  /// Android program_round_of
  ///
  /// In en, this message translates to:
  /// **'Round {current} of {total}'**
  String programRoundOf(int current, int total);

  /// Android program_round_rest
  ///
  /// In en, this message translates to:
  /// **'Rest between rounds'**
  String get programRoundRest;

  /// Android program_work
  ///
  /// In en, this message translates to:
  /// **'Work'**
  String get programWork;

  /// Android program_work_rest_seconds
  ///
  /// In en, this message translates to:
  /// **'{work} s work / {rest} s rest'**
  String programWorkRestSeconds(int work, int rest);

  /// Android program_skip
  ///
  /// In en, this message translates to:
  /// **'Skip'**
  String get programSkip;

  /// Android groups_member_fallback_name
  ///
  /// In en, this message translates to:
  /// **'Member'**
  String get memberFallbackName;

  /// Android sign_out_pending_title
  ///
  /// In en, this message translates to:
  /// **'Changes not backed up'**
  String get signOutPendingTitle;

  /// Web only (Android: sign_out_pending_message)
  ///
  /// In en, this message translates to:
  /// **'Some changes are saved only in this browser and will be lost if you sign out now. Reconnect to send them, or sign out anyway.'**
  String get signOutPendingText;

  /// Android sign_out_anyway
  ///
  /// In en, this message translates to:
  /// **'Sign out anyway'**
  String get signOutAnyway;

  /// Android gym_routines_button
  ///
  /// In en, this message translates to:
  /// **'Gym routines'**
  String get gymTitle;

  /// Android gym_start_routine
  ///
  /// In en, this message translates to:
  /// **'Start routine'**
  String get gymStartRoutine;

  /// Android gym_empty_catalog
  ///
  /// In en, this message translates to:
  /// **'No gym routines available'**
  String get gymEmptyCatalog;

  /// Android gym_summary
  ///
  /// In en, this message translates to:
  /// **'{sets} · {values}'**
  String gymSummary(String sets, String values);

  /// Android gym_summary_sets
  ///
  /// In en, this message translates to:
  /// **'Sets: {count}'**
  String gymSummarySets(int count);

  /// Android gym_summary_reps
  ///
  /// In en, this message translates to:
  /// **'Reps: {values}'**
  String gymSummaryReps(String values);

  /// Android gym_summary_time
  ///
  /// In en, this message translates to:
  /// **'Time: {values}'**
  String gymSummaryTime(String values);

  /// Android gym_summary_seconds
  ///
  /// In en, this message translates to:
  /// **'{seconds}s'**
  String gymSummarySeconds(int seconds);

  /// Android gym_state_expanded
  ///
  /// In en, this message translates to:
  /// **'Expanded'**
  String get gymStateExpanded;

  /// Android gym_state_collapsed
  ///
  /// In en, this message translates to:
  /// **'Collapsed'**
  String get gymStateCollapsed;

  /// Android gym_show_exercises
  ///
  /// In en, this message translates to:
  /// **'Show exercises'**
  String get gymShowExercises;

  /// Android gym_hide_exercises
  ///
  /// In en, this message translates to:
  /// **'Hide exercises'**
  String get gymHideExercises;

  /// Android gym_state_completed
  ///
  /// In en, this message translates to:
  /// **'Completed'**
  String get gymStateCompleted;

  /// Android gym_state_not_completed
  ///
  /// In en, this message translates to:
  /// **'Not completed'**
  String get gymStateNotCompleted;

  /// Android gym_routine_reset_progress
  ///
  /// In en, this message translates to:
  /// **'Reset progress?'**
  String get gymResetProgressTitle;

  /// Android gym_routine_reset_action_description
  ///
  /// In en, this message translates to:
  /// **'Reset progress'**
  String get gymResetAction;

  /// Android gym_routine_reset_confirmation
  ///
  /// In en, this message translates to:
  /// **'All completion marks of this routine will be cleared. This cannot be undone.'**
  String get gymResetText;

  /// Android gym_routine_reset
  ///
  /// In en, this message translates to:
  /// **'Reset'**
  String get gymReset;

  /// Android gym_routine_completed_count (by total, as the web's other X-of-Y plurals)
  ///
  /// In en, this message translates to:
  /// **'{total, plural, =1{{done} of {total} exercise completed} other{{done} of {total} exercises completed}}'**
  String gymCompletedCount(int done, int total);

  /// Android gym_routine_empty
  ///
  /// In en, this message translates to:
  /// **'This routine has no exercises yet.'**
  String get gymRoutineEmpty;

  /// Android gym_routine_completed_title
  ///
  /// In en, this message translates to:
  /// **'Routine completed!'**
  String get gymCompletedTitle;

  /// Android gym_routine_completed_message
  ///
  /// In en, this message translates to:
  /// **'Great job! You finished every exercise of this routine.'**
  String get gymCompletedText;

  /// Android gym_routine_finish
  ///
  /// In en, this message translates to:
  /// **'Finish'**
  String get gymFinish;

  /// Android gym_routine_not_found
  ///
  /// In en, this message translates to:
  /// **'Routine not found'**
  String get gymRoutineNotFound;

  /// Android gym_exercise_set_x_of_y
  ///
  /// In en, this message translates to:
  /// **'Set {current} of {total}'**
  String gymSetOf(int current, int total);

  /// Android gym_exercise_set_done
  ///
  /// In en, this message translates to:
  /// **'Set done'**
  String get gymSetDone;

  /// Android gym_exercise_start
  ///
  /// In en, this message translates to:
  /// **'Start'**
  String get gymStart;

  /// Android gym_exercise_get_ready
  ///
  /// In en, this message translates to:
  /// **'Get ready'**
  String get gymGetReady;

  /// Android gym_exercise_rest
  ///
  /// In en, this message translates to:
  /// **'Rest'**
  String get gymRest;

  /// Android gym_exercise_next_set
  ///
  /// In en, this message translates to:
  /// **'Next: set {current} of {total}'**
  String gymNextSet(int current, int total);

  /// Android gym_exercise_skip_rest
  ///
  /// In en, this message translates to:
  /// **'Skip rest'**
  String get gymSkipRest;

  /// Android gym_exercise_exit_title
  ///
  /// In en, this message translates to:
  /// **'Leave exercise?'**
  String get gymExitTitle;

  /// Android gym_exercise_exit_message (plural on the completed count, as Android)
  ///
  /// In en, this message translates to:
  /// **'{done, plural, =1{Progress: {done} of {total} set completed. If you leave now, this progress will not be saved and the exercise will not be marked as completed.} other{Progress: {done} of {total} sets completed. If you leave now, this progress will not be saved and the exercise will not be marked as completed.}}'**
  String gymExitText(int done, int total);

  /// Android gym_exercise_exit_confirm
  ///
  /// In en, this message translates to:
  /// **'Leave'**
  String get gymExitConfirm;

  /// Android gym_exercise_not_found
  ///
  /// In en, this message translates to:
  /// **'Exercise not found'**
  String get gymExerciseNotFound;

  /// Web only: gym progress is local, as on Android (not synced)
  ///
  /// In en, this message translates to:
  /// **'Gym progress stays in this browser: a routine started on another device continues there.'**
  String get gymBrowserNote;

  /// Android start_today_routine (Home's primary button)
  ///
  /// In en, this message translates to:
  /// **'Start Today\'s Routine'**
  String get trainStartToday;

  /// Android home_day_available
  ///
  /// In en, this message translates to:
  /// **'{day} available'**
  String trainDayAvailable(String day);

  /// Android program_home_button
  ///
  /// In en, this message translates to:
  /// **'Programs'**
  String get trainPrograms;

  /// Android select_routine
  ///
  /// In en, this message translates to:
  /// **'Select Routine'**
  String get dailySelectTitle;

  /// Web only: the day Home calls available, highlighted in the daily selection
  ///
  /// In en, this message translates to:
  /// **'Available today'**
  String get dailyAvailableToday;

  /// Web only: start this daily routine (Android opens it by tapping the card; text as gym_exercise_start)
  ///
  /// In en, this message translates to:
  /// **'Start'**
  String get dailyStart;

  /// Android daily_locked_hint
  ///
  /// In en, this message translates to:
  /// **'Complete {day} to unlock'**
  String dailyLockedHint(String day);

  /// Android daily_state_locked (a day's state for screen readers)
  ///
  /// In en, this message translates to:
  /// **'Locked'**
  String get dailyStateLocked;

  /// Android daily_state_completed
  ///
  /// In en, this message translates to:
  /// **'Completed'**
  String get dailyStateCompleted;

  /// Android daily_state_available
  ///
  /// In en, this message translates to:
  /// **'Available'**
  String get dailyStateAvailable;

  /// Android daily_restart_action
  ///
  /// In en, this message translates to:
  /// **'Restart days'**
  String get dailyRestartAction;

  /// Android daily_restart_title
  ///
  /// In en, this message translates to:
  /// **'Restart days?'**
  String get dailyRestartTitle;

  /// Android daily_restart_message
  ///
  /// In en, this message translates to:
  /// **'Every day will be locked again and you\'ll start from {day}. Your history, your weekly goal and your groups won\'t change.'**
  String dailyRestartMessage(String day);

  /// Android daily_restart_confirm
  ///
  /// In en, this message translates to:
  /// **'Restart'**
  String get dailyRestartConfirm;

  /// Android daily_cycle_complete
  ///
  /// In en, this message translates to:
  /// **'You completed every day!'**
  String get dailyCycleComplete;

  /// Android daily_start_again
  ///
  /// In en, this message translates to:
  /// **'Start again from {day}'**
  String dailyStartAgain(String day);

  /// Android photo_add
  ///
  /// In en, this message translates to:
  /// **'Add photo'**
  String get photoAdd;

  /// Android photo_change
  ///
  /// In en, this message translates to:
  /// **'Change photo'**
  String get photoChange;

  /// Android photo_remove
  ///
  /// In en, this message translates to:
  /// **'Remove photo'**
  String get photoRemove;

  /// Web version of Android photo_privacy_note (the browser keeps the photo, the feed gets the thumbnail)
  ///
  /// In en, this message translates to:
  /// **'Photos stay in this browser; your groups only see a small preview. Location data is removed.'**
  String get photoPrivacyNote;

  /// Android photo_processing
  ///
  /// In en, this message translates to:
  /// **'Saving photo…'**
  String get photoProcessing;

  /// Android photo_added
  ///
  /// In en, this message translates to:
  /// **'Photo added'**
  String get photoAdded;

  /// Android photo_error_unreadable
  ///
  /// In en, this message translates to:
  /// **'This image couldn\'t be read. Try another one.'**
  String get photoErrorUnreadable;

  /// Web only: a HEIC file the browser can't decode
  ///
  /// In en, this message translates to:
  /// **'This browser can\'t read HEIC photos. Choose a JPEG or PNG photo, or set the camera format to Most Compatible.'**
  String get photoErrorHeic;

  /// Web only: the input file is over the limit
  ///
  /// In en, this message translates to:
  /// **'This photo is too large (maximum {maxMb} MB). Choose a smaller one.'**
  String photoErrorTooLarge(int maxMb);

  /// Web version of Android photo_error_no_space
  ///
  /// In en, this message translates to:
  /// **'Not enough space in this browser to save the photo.'**
  String get photoErrorNoSpace;

  /// Android photo_error_failed
  ///
  /// In en, this message translates to:
  /// **'The photo couldn\'t be saved. Your workout is safe.'**
  String get photoErrorFailed;

  /// Android photo_content_description
  ///
  /// In en, this message translates to:
  /// **'Workout photo, {date}'**
  String photoContentDescription(String date);

  /// Android photo_content_description_undated
  ///
  /// In en, this message translates to:
  /// **'Workout photo'**
  String get photoContentDescriptionUndated;

  /// Android photo_open
  ///
  /// In en, this message translates to:
  /// **'Open photo'**
  String get photoOpen;

  /// Android photo_close
  ///
  /// In en, this message translates to:
  /// **'Close photo'**
  String get photoClose;

  /// Android photo_remove_title
  ///
  /// In en, this message translates to:
  /// **'Remove the photo?'**
  String get photoRemoveTitle;

  /// Web version of Android photo_remove_message
  ///
  /// In en, this message translates to:
  /// **'The photo will be deleted from this browser and from your posts in your groups.'**
  String get photoRemoveMessage;

  /// Android photo_remove_confirm
  ///
  /// In en, this message translates to:
  /// **'Remove'**
  String get photoRemoveConfirm;

  /// Web only: desktop drag and drop
  ///
  /// In en, this message translates to:
  /// **'You can also drop a photo here.'**
  String get photoDropHint;

  /// Web only: the full-screen photo viewer
  ///
  /// In en, this message translates to:
  /// **'Photos added on another device stay on that device.'**
  String get photoOtherDevices;

  /// Web only: the one-time hint in Safari on iPhone and iPad
  ///
  /// In en, this message translates to:
  /// **'Add Tio Minuter to your Home Screen so your photos stay saved.'**
  String get installHintText;

  /// Web only: close the Home Screen hint
  ///
  /// In en, this message translates to:
  /// **'Dismiss'**
  String get installHintDismiss;

  /// Web only: back from Google's sign-in page without a session (blocked storage, private mode)
  ///
  /// In en, this message translates to:
  /// **'Sign-in didn\'t finish in this browser. Try again; if it keeps happening, allow cookies for this site or use another browser.'**
  String get signInRedirectIncomplete;

  /// Android groups_empty_text
  ///
  /// In en, this message translates to:
  /// **'Create a group and share its code, or join one with a code from a friend.'**
  String get groupsEmptyText;

  /// Android groups_create_button
  ///
  /// In en, this message translates to:
  /// **'Create group'**
  String get groupsCreateButton;

  /// Android groups_join_button
  ///
  /// In en, this message translates to:
  /// **'Join with code'**
  String get groupsJoinButton;

  /// Android groups_privacy_note
  ///
  /// In en, this message translates to:
  /// **'Groups see your name, photo, weekly count, goal, streak and which kinds of workout you did each day. Never durations, notes or photos.'**
  String get groupsPrivacyNote;

  /// Android groups_my_position
  ///
  /// In en, this message translates to:
  /// **'This week: #{position}'**
  String groupsMyPosition(int position);

  /// Android groups_goal_required_title
  ///
  /// In en, this message translates to:
  /// **'Set your weekly goal first'**
  String get groupsGoalRequiredTitle;

  /// Android groups_goal_required_text
  ///
  /// In en, this message translates to:
  /// **'Groups compare everyone\'s week against their own goal, so you need one before creating or joining a group.'**
  String get groupsGoalRequiredText;

  /// Android groups_goal_required_action
  ///
  /// In en, this message translates to:
  /// **'Set goal'**
  String get groupsGoalRequiredAction;

  /// Android groups_create_title
  ///
  /// In en, this message translates to:
  /// **'Create a group'**
  String get groupsCreateTitle;

  /// Android groups_create_name_label
  ///
  /// In en, this message translates to:
  /// **'Group name'**
  String get groupsCreateNameLabel;

  /// Android groups_create_name_error
  ///
  /// In en, this message translates to:
  /// **'Use 1 to 40 characters.'**
  String get groupsCreateNameError;

  /// Android groups_create_action
  ///
  /// In en, this message translates to:
  /// **'Create'**
  String get groupsCreateAction;

  /// Android groups_created_title
  ///
  /// In en, this message translates to:
  /// **'Group created!'**
  String get groupsCreatedTitle;

  /// Android groups_created_text
  ///
  /// In en, this message translates to:
  /// **'Share this code so your friends can join {name}.'**
  String groupsCreatedText(String name);

  /// Android groups_open_group
  ///
  /// In en, this message translates to:
  /// **'Open group'**
  String get groupsOpenGroup;

  /// Android groups_code_label
  ///
  /// In en, this message translates to:
  /// **'Invite code'**
  String get groupsCodeLabel;

  /// Android groups_copy_code
  ///
  /// In en, this message translates to:
  /// **'Copy code'**
  String get groupsCopyCode;

  /// Android groups_share_code
  ///
  /// In en, this message translates to:
  /// **'Share code'**
  String get groupsShareCode;

  /// Android groups_code_copied
  ///
  /// In en, this message translates to:
  /// **'Code copied'**
  String get groupsCodeCopied;

  /// Android groups_share_text
  ///
  /// In en, this message translates to:
  /// **'Join my group {name} on {app} with code {code}'**
  String groupsShareText(String name, String code, String app);

  /// Android groups_join_title
  ///
  /// In en, this message translates to:
  /// **'Join a group'**
  String get groupsJoinTitle;

  /// Android groups_join_code_label
  ///
  /// In en, this message translates to:
  /// **'Invite code'**
  String get groupsJoinCodeLabel;

  /// Android groups_join_code_hint
  ///
  /// In en, this message translates to:
  /// **'8 characters, for example K7MPX2QA'**
  String get groupsJoinCodeHint;

  /// Android groups_join_code_invalid_chars
  ///
  /// In en, this message translates to:
  /// **'Codes never use 0, O, 1, I or L.'**
  String get groupsJoinCodeInvalidChars;

  /// Android groups_join_looking
  ///
  /// In en, this message translates to:
  /// **'Looking for the group…'**
  String get groupsJoinLooking;

  /// Android groups_join_preview
  ///
  /// In en, this message translates to:
  /// **'You\'re about to join {name}.'**
  String groupsJoinPreview(String name);

  /// Android groups_join_action
  ///
  /// In en, this message translates to:
  /// **'Join'**
  String get groupsJoinAction;

  /// Android groups_detail_leave
  ///
  /// In en, this message translates to:
  /// **'Leave group'**
  String get groupsLeave;

  /// Android groups_detail_owner_menu
  ///
  /// In en, this message translates to:
  /// **'Group options'**
  String get groupsOwnerMenu;

  /// Android groups_detail_member_menu
  ///
  /// In en, this message translates to:
  /// **'Options for {name}'**
  String groupsMemberMenu(String name);

  /// Android groups_detail_rename
  ///
  /// In en, this message translates to:
  /// **'Rename'**
  String get groupsRename;

  /// Android groups_detail_regenerate
  ///
  /// In en, this message translates to:
  /// **'New invite code'**
  String get groupsRegenerate;

  /// Android groups_detail_transfer
  ///
  /// In en, this message translates to:
  /// **'Transfer ownership'**
  String get groupsTransfer;

  /// Android groups_detail_delete
  ///
  /// In en, this message translates to:
  /// **'Delete group'**
  String get groupsDelete;

  /// Android groups_detail_remove
  ///
  /// In en, this message translates to:
  /// **'Remove from group'**
  String get groupsRemove;

  /// Android groups_detail_make_owner
  ///
  /// In en, this message translates to:
  /// **'Make owner'**
  String get groupsMakeOwner;

  /// Android groups_dialog_rename_title
  ///
  /// In en, this message translates to:
  /// **'Rename group'**
  String get groupsDialogRenameTitle;

  /// Android groups_dialog_save
  ///
  /// In en, this message translates to:
  /// **'Save'**
  String get groupsDialogSave;

  /// Android groups_dialog_regenerate_title
  ///
  /// In en, this message translates to:
  /// **'Create a new invite code?'**
  String get groupsDialogRegenerateTitle;

  /// Android groups_dialog_regenerate_text
  ///
  /// In en, this message translates to:
  /// **'The current code will stop working. People already in the group stay.'**
  String get groupsDialogRegenerateText;

  /// Android groups_dialog_regenerate_confirm
  ///
  /// In en, this message translates to:
  /// **'New code'**
  String get groupsDialogRegenerateConfirm;

  /// Android groups_dialog_remove_title
  ///
  /// In en, this message translates to:
  /// **'Remove {name}?'**
  String groupsDialogRemoveTitle(String name);

  /// Android groups_dialog_remove_text
  ///
  /// In en, this message translates to:
  /// **'They\'ll leave the group. They can join again with the current code.'**
  String get groupsDialogRemoveText;

  /// Android groups_dialog_remove_confirm
  ///
  /// In en, this message translates to:
  /// **'Remove'**
  String get groupsDialogRemoveConfirm;

  /// Android groups_dialog_transfer_title
  ///
  /// In en, this message translates to:
  /// **'Choose the new owner'**
  String get groupsDialogTransferTitle;

  /// Android groups_dialog_transfer_before_leaving
  ///
  /// In en, this message translates to:
  /// **'You own this group. Choose who will own it before you leave.'**
  String get groupsDialogTransferBeforeLeaving;

  /// Android groups_dialog_transfer_confirm_title
  ///
  /// In en, this message translates to:
  /// **'Make {name} the owner?'**
  String groupsDialogTransferConfirmTitle(String name);

  /// Android groups_dialog_transfer_confirm_text
  ///
  /// In en, this message translates to:
  /// **'They\'ll be able to rename, manage members and delete the group.'**
  String get groupsDialogTransferConfirmText;

  /// Android groups_dialog_transfer_and_leave_text
  ///
  /// In en, this message translates to:
  /// **'They\'ll own the group and you\'ll leave it.'**
  String get groupsDialogTransferAndLeaveText;

  /// Android groups_dialog_transfer_confirm
  ///
  /// In en, this message translates to:
  /// **'Make owner'**
  String get groupsDialogTransferConfirm;

  /// Android groups_dialog_transfer_and_leave_confirm
  ///
  /// In en, this message translates to:
  /// **'Transfer and leave'**
  String get groupsDialogTransferAndLeaveConfirm;

  /// Android groups_dialog_delete_title
  ///
  /// In en, this message translates to:
  /// **'Delete {name}?'**
  String groupsDialogDeleteTitle(String name);

  /// Android groups_dialog_delete_text
  ///
  /// In en, this message translates to:
  /// **'Everyone will leave the group and its code will stop working.'**
  String get groupsDialogDeleteText;

  /// Android groups_dialog_delete_next
  ///
  /// In en, this message translates to:
  /// **'Continue'**
  String get groupsDialogDeleteNext;

  /// Android groups_dialog_delete_confirm_title
  ///
  /// In en, this message translates to:
  /// **'This can\'t be undone'**
  String get groupsDialogDeleteConfirmTitle;

  /// Android groups_dialog_delete_confirm_text
  ///
  /// In en, this message translates to:
  /// **'Delete {name} for every member?'**
  String groupsDialogDeleteConfirmText(String name);

  /// Android groups_dialog_delete_confirm
  ///
  /// In en, this message translates to:
  /// **'Delete group'**
  String get groupsDialogDeleteConfirm;

  /// Android groups_dialog_leave_title
  ///
  /// In en, this message translates to:
  /// **'Leave {name}?'**
  String groupsDialogLeaveTitle(String name);

  /// Android groups_dialog_leave_text
  ///
  /// In en, this message translates to:
  /// **'You can join again with the group\'s code.'**
  String get groupsDialogLeaveText;

  /// Android groups_dialog_leave_alone_text
  ///
  /// In en, this message translates to:
  /// **'You\'re the only member, so leaving deletes the group.'**
  String get groupsDialogLeaveAloneText;

  /// Android groups_dialog_leave_confirm
  ///
  /// In en, this message translates to:
  /// **'Leave'**
  String get groupsDialogLeaveConfirm;

  /// Android groups_message_no_network
  ///
  /// In en, this message translates to:
  /// **'No internet connection. Try again when you\'re online.'**
  String get groupsMessageNoNetwork;

  /// Android groups_message_invalid_code
  ///
  /// In en, this message translates to:
  /// **'That code doesn\'t match any group. Check it and try again.'**
  String get groupsMessageInvalidCode;

  /// Android groups_message_group_full
  ///
  /// In en, this message translates to:
  /// **'That group is full (20 members).'**
  String get groupsMessageGroupFull;

  /// Android groups_message_already_member
  ///
  /// In en, this message translates to:
  /// **'You\'re already in that group.'**
  String get groupsMessageAlreadyMember;

  /// Android groups_message_group_limit
  ///
  /// In en, this message translates to:
  /// **'You can be in up to 5 groups. Leave one to join another.'**
  String get groupsMessageGroupLimit;

  /// Android groups_message_goal_required
  ///
  /// In en, this message translates to:
  /// **'Set your weekly goal first.'**
  String get groupsMessageGoalRequired;

  /// Android groups_message_invalid_name
  ///
  /// In en, this message translates to:
  /// **'Use 1 to 40 characters.'**
  String get groupsMessageInvalidName;

  /// Android groups_message_not_found
  ///
  /// In en, this message translates to:
  /// **'This group is no longer available to you.'**
  String get groupsMessageNotFound;

  /// Android groups_message_not_allowed
  ///
  /// In en, this message translates to:
  /// **'That action isn\'t allowed.'**
  String get groupsMessageNotAllowed;

  /// Android groups_message_failed
  ///
  /// In en, this message translates to:
  /// **'Something went wrong. Try again.'**
  String get groupsMessageFailed;

  /// Android groups_message_left
  ///
  /// In en, this message translates to:
  /// **'You left the group.'**
  String get groupsMessageLeft;

  /// Android groups_message_deleted
  ///
  /// In en, this message translates to:
  /// **'Group deleted.'**
  String get groupsMessageDeleted;

  /// Android groups_message_renamed
  ///
  /// In en, this message translates to:
  /// **'Group renamed.'**
  String get groupsMessageRenamed;

  /// Android groups_message_code_regenerated
  ///
  /// In en, this message translates to:
  /// **'New code: {code}'**
  String groupsMessageCodeRegenerated(String code);

  /// Android groups_message_member_removed
  ///
  /// In en, this message translates to:
  /// **'{name} was removed.'**
  String groupsMessageMemberRemoved(String name);

  /// Android groups_message_owner_changed
  ///
  /// In en, this message translates to:
  /// **'{name} is now the owner.'**
  String groupsMessageOwnerChanged(String name);

  /// Android trained_today_one
  ///
  /// In en, this message translates to:
  /// **'{name} already trained today. Your turn 💪'**
  String trainedTodayOne(String name);

  /// Android trained_today_two
  ///
  /// In en, this message translates to:
  /// **'{first} and {second} already trained today. Your turn 💪'**
  String trainedTodayTwo(String first, String second);

  /// Android trained_today_open
  ///
  /// In en, this message translates to:
  /// **'Open groups'**
  String get trainedTodayOpen;

  /// Android trained_today_dismiss
  ///
  /// In en, this message translates to:
  /// **'Hide for today'**
  String get trainedTodayDismiss;

  /// Android feed_delete
  ///
  /// In en, this message translates to:
  /// **'Delete'**
  String get feedDelete;

  /// Android feed_hide
  ///
  /// In en, this message translates to:
  /// **'Hide for me'**
  String get feedHide;

  /// Android feed_post_menu
  ///
  /// In en, this message translates to:
  /// **'Options for the workout of {name}'**
  String feedPostMenu(String name);

  /// Android feed_dialog_delete_own_title
  ///
  /// In en, this message translates to:
  /// **'Delete your post?'**
  String get feedDialogDeleteOwnTitle;

  /// Android feed_dialog_delete_own_text
  ///
  /// In en, this message translates to:
  /// **'It disappears from this group\'s feed. The workout stays in your history.'**
  String get feedDialogDeleteOwnText;

  /// Android feed_dialog_delete_owner_title
  ///
  /// In en, this message translates to:
  /// **'Delete the post of {name}?'**
  String feedDialogDeleteOwnerTitle(String name);

  /// Android feed_dialog_delete_owner_text
  ///
  /// In en, this message translates to:
  /// **'It disappears from the group\'s feed for everyone. Their workout stays in their own history.'**
  String get feedDialogDeleteOwnerText;

  /// Android feed_message_hidden
  ///
  /// In en, this message translates to:
  /// **'Post hidden on this device'**
  String get feedMessageHidden;

  /// Android feed_message_deleted
  ///
  /// In en, this message translates to:
  /// **'Post deleted'**
  String get feedMessageDeleted;

  /// Android account_delete
  ///
  /// In en, this message translates to:
  /// **'Delete account'**
  String get accountDelete;

  /// Android delete_dialog_title
  ///
  /// In en, this message translates to:
  /// **'Delete your account?'**
  String get deleteDialogTitle;

  /// Android delete_continue
  ///
  /// In en, this message translates to:
  /// **'Continue'**
  String get deleteContinue;

  /// Android delete_confirm_title
  ///
  /// In en, this message translates to:
  /// **'Delete permanently?'**
  String get deleteConfirmTitle;

  /// Android auth_error_groups_failed
  ///
  /// In en, this message translates to:
  /// **'Couldn\'t leave your groups, so nothing was deleted. Try again.'**
  String get authErrorGroupsFailed;

  /// Android auth_error_server_data_failed
  ///
  /// In en, this message translates to:
  /// **'Couldn\'t delete your backed-up data, so your account wasn\'t deleted. Try again.'**
  String get authErrorServerDataFailed;

  /// Android auth_error_account_not_deleted
  ///
  /// In en, this message translates to:
  /// **'Your backed-up data was deleted, but your account couldn\'t be deleted. Try Delete account again, or sign out.'**
  String get authErrorAccountNotDeleted;

  /// Android auth_error_delete_failed
  ///
  /// In en, this message translates to:
  /// **'Couldn\'t delete the account. Try again.'**
  String get authErrorDeleteFailed;

  /// Android trained_today_more
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{{first}, {second} and {count} more already trained today. Your turn 💪} other{{first}, {second} and {count} more already trained today. Your turn 💪}}'**
  String trainedTodayMore(String first, String second, int count);

  /// Web only: no share sheet in this browser, the invite text was copied
  ///
  /// In en, this message translates to:
  /// **'Invite copied: paste it in a message to your friends.'**
  String get groupsShareCopied;

  /// Web only: the code is incomplete
  ///
  /// In en, this message translates to:
  /// **'Enter the 8 characters of the code.'**
  String get groupsJoinCodeLength;

  /// Web only: leaving deletes my posts (as Android)
  ///
  /// In en, this message translates to:
  /// **'Your posts in a group are deleted when you leave it.'**
  String get groupsPostsNote;

  /// Web version of Android delete_dialog_message (no 'also delete from this device' choice: the browser's data always goes)
  ///
  /// In en, this message translates to:
  /// **'Your Tio Minuter account and everything backed up online (workouts, goals, programs, your groups and your posts) are deleted. First you\'ll confirm with Google. What this browser keeps for this account (photos, workouts in progress) is removed too.'**
  String get deleteDialogMessageWeb;

  /// Web version of Android delete_confirm_message_*
  ///
  /// In en, this message translates to:
  /// **'This can\'t be undone. Your account, your cloud data and this browser\'s data for it are deleted.'**
  String get deleteConfirmMessageWeb;

  /// Web only: the deletion runs (re-authenticated)
  ///
  /// In en, this message translates to:
  /// **'Deleting your account…'**
  String get deleteInProgress;

  /// Web version of Android auth_account_deleted (no guest mode on the web)
  ///
  /// In en, this message translates to:
  /// **'Account deleted.'**
  String get accountDeletedWeb;

  /// Web only: re-authentication with another Google account
  ///
  /// In en, this message translates to:
  /// **'Choose the Google account you\'re signed in with, then try again.'**
  String get deleteWrongAccount;
}

class _AppLocalizationsDelegate extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  Future<AppLocalizations> load(Locale locale) {
    return SynchronousFuture<AppLocalizations>(lookupAppLocalizations(locale));
  }

  @override
  bool isSupported(Locale locale) => <String>['en', 'es'].contains(locale.languageCode);

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}

AppLocalizations lookupAppLocalizations(Locale locale) {
  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'en':
      return AppLocalizationsEn();
    case 'es':
      return AppLocalizationsEs();
  }

  throw FlutterError(
    'AppLocalizations.delegate failed to load unsupported locale "$locale". This is likely '
    'an issue with the localizations generation tool. Please file an issue '
    'on GitHub with a reproducible sample app and the gen-l10n configuration '
    'that was used.',
  );
}

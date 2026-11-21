// ignore: unused_import
import 'package:intl/intl.dart' as intl;

import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Spanish Castilian (`es`).
class AppLocalizationsEs extends AppLocalizations {
  AppLocalizationsEs([String locale = 'es']) : super(locale);

  @override
  String get appName => 'Tío Minuter';

  @override
  String get tagline => 'Sin excusas — solo 10 minutos al día.';

  @override
  String get landingTitle => 'Tu semana, tu historial y tus grupos, en la web';

  @override
  String get landingText =>
      'Tío Minuter te guía en entrenos diarios cortos, rutinas de gimnasio y programas desde tu teléfono Android. Inicia sesión con la misma cuenta de Google para ver tu meta semanal, tu historial de entrenos y cómo van tus grupos.';

  @override
  String get landingTrainOnPhone =>
      'Entrena con las rutinas diarias, tus programas y las actividades que registres, aquí o en la app de Android.';

  @override
  String get signInWithGoogle => 'Iniciar sesión con Google';

  @override
  String get signingIn => 'Iniciando sesión…';

  @override
  String get signInFailed => 'No se pudo iniciar sesión con Google. Inténtalo de nuevo.';

  @override
  String get noNetwork => 'Sin conexión a internet. Revísala e inténtalo de nuevo.';

  @override
  String get popupBlocked =>
      'Se bloqueó la ventana para iniciar sesión. Permite las ventanas emergentes de este sitio o inténtalo de nuevo.';

  @override
  String get navWeek => 'Esta semana';

  @override
  String get navHistory => 'Historial';

  @override
  String get navGroups => 'Grupos';

  @override
  String get navAccount => 'Cuenta';

  @override
  String get weeklyGoalTitle => 'Meta semanal';

  @override
  String get weekNoGoal => 'Define tu meta semanal';

  @override
  String get weekNoGoalHint => 'Defínela en la app de Android: Inicio → Esta semana.';

  @override
  String weekProgress(int done, int goal) {
    String _temp0 = intl.Intl.pluralLogic(
      done,
      locale: localeName,
      other: '$done de $goal entrenos',
      one: '$done de $goal entreno',
    );
    return '$_temp0';
  }

  @override
  String get weekGoalMet => '¡Meta de la semana cumplida!';

  @override
  String weekWorkoutsLeft(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Te faltan $count entrenos para tu meta',
      one: 'Te falta $count entreno para tu meta',
    );
    return '$_temp0';
  }

  @override
  String weekStreak(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Racha: $count semanas',
      one: 'Racha: $count semana',
    );
    return '$_temp0';
  }

  @override
  String weekCountingRule(int minutes) {
    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other:
          'Cuenta cualquier entreno de al menos $minutes minutos: rutinas diarias, rutinas de gimnasio, entrenos de programas y las actividades que registres. Dos entrenos el mismo día cuentan como dos.',
      one:
          'Cuenta cualquier entreno de al menos $minutes minuto: rutinas diarias, rutinas de gimnasio, entrenos de programas y las actividades que registres. Dos entrenos el mismo día cuentan como dos.',
    );
    return '$_temp0';
  }

  @override
  String weekDayNone(String day) {
    return '$day: sin entrenos';
  }

  @override
  String weekDaySources(String day, String sources) {
    return '$day: $sources';
  }

  @override
  String get weekToday => 'hoy';

  @override
  String get historyTitle => 'Historial de entrenos';

  @override
  String get historyEmpty =>
      'Aquí aparecen los entrenos completados: rutinas diarias, rutinas de gimnasio, entrenos de los programas y las actividades que registres.';

  @override
  String get historyToday => 'Hoy';

  @override
  String get historyYesterday => 'Ayer';

  @override
  String get historyNotCounted => 'No cuenta para la meta';

  @override
  String get historyLoadMore => 'Cargar más';

  @override
  String durationMinutes(int minutes) {
    return '$minutes min';
  }

  @override
  String get sourceDaily => 'Rutina diaria';

  @override
  String get sourceGym => 'Rutina de gimnasio';

  @override
  String get sourceProgram => 'Programa';

  @override
  String get sourceActivity => 'Actividad';

  @override
  String workoutNameDay(int day) {
    return 'Día $day';
  }

  @override
  String workoutNameDayWithTitle(String day, String title) {
    return '$day · $title';
  }

  @override
  String get programTitleFullBodyA => 'Cuerpo completo A';

  @override
  String get programTitleFullBodyB => 'Cuerpo completo B';

  @override
  String get programTitleFullBodyC => 'Cuerpo completo C';

  @override
  String get programTitleUpperA => 'Tren superior A';

  @override
  String get programTitleUpperB => 'Tren superior B';

  @override
  String get programTitleLowerA => 'Tren inferior A';

  @override
  String get programTitleLowerB => 'Tren inferior B';

  @override
  String get programTitlePush => 'Empuje';

  @override
  String get programTitlePull => 'Tirón';

  @override
  String get programTitleLegs => 'Piernas';

  @override
  String get programTitleFullBody => 'Cuerpo completo';

  @override
  String get programTitleUpper => 'Tren superior';

  @override
  String get programTitleLower => 'Tren inferior';

  @override
  String get programTitleUpperC => 'Tren superior C';

  @override
  String get programTitleLowerC => 'Tren inferior C';

  @override
  String get programTitleOutdoorRun => 'Carrera al aire libre';

  @override
  String get programTitleBriskWalk => 'Caminata rápida';

  @override
  String get programTitleTreadmill => 'Cinta de correr';

  @override
  String get programTitleHomeCircuit => 'Circuito en casa';

  @override
  String get activityOwnGymRoutine => 'Rutina propia de gimnasio';

  @override
  String get activityRunning => 'Correr';

  @override
  String get activityWalking => 'Caminar';

  @override
  String get activityCycling => 'Ciclismo';

  @override
  String get activitySwimming => 'Natación';

  @override
  String get activityYoga => 'Yoga';

  @override
  String get activityPilates => 'Pilates';

  @override
  String get activityDance => 'Baile';

  @override
  String get activityTeamSport => 'Deporte de equipo';

  @override
  String get activityRacketSport => 'Deporte de raqueta';

  @override
  String get activityMartialArts => 'Artes marciales';

  @override
  String get activityHiit => 'HIIT';

  @override
  String get activityStretching => 'Estiramientos';

  @override
  String get activityOther => 'Otra';

  @override
  String get groupsTitle => 'Grupos';

  @override
  String get groupsEmptyTitle => 'Todavía no tienes grupos';

  @override
  String get groupsOwner => 'A cargo del grupo';

  @override
  String groupsMemberCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(count, locale: localeName, other: '$count miembros', one: '$count miembro');
    return '$_temp0';
  }

  @override
  String get groupsTabRanking => 'Ranking';

  @override
  String get groupsTabFeed => 'Actividad';

  @override
  String get groupsThisWeek => 'Esta semana';

  @override
  String get groupsMe => 'Tú';

  @override
  String groupsProgress(int done, int goal) {
    return '$done/$goal';
  }

  @override
  String groupsStreak(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Racha: $count semanas',
      one: 'Racha: $count semana',
    );
    return '$_temp0';
  }

  @override
  String groupsPosition(int position) {
    return '#$position';
  }

  @override
  String get groupsNotUpdated => 'Sin datos de esta semana';

  @override
  String get groupsGone => 'Este grupo ya no está disponible.';

  @override
  String get feedEmpty => 'Todavía no hay entrenamientos compartidos esta semana.';

  @override
  String get feedEmptyMonday =>
      'Hoy empieza una semana nueva. Aquí aparecerán los entrenamientos que termine tu grupo.';

  @override
  String get feedLoadMore => 'Cargar más';

  @override
  String feedPhotoDescription(String name) {
    return 'Foto del entreno de $name';
  }

  @override
  String feedReactionDescription(String label, int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$label, $count reacciones',
      one: '$label, $count reacción',
    );
    return '$_temp0';
  }

  @override
  String get feedReactionMine => 'Tu reacción';

  @override
  String get feedReactionNotMine => 'Sin seleccionar';

  @override
  String get feedReactionStrong => 'Fuerza';

  @override
  String get feedReactionFire => 'A tope';

  @override
  String get feedReactionClap => 'Aplausos';

  @override
  String get feedReactionRaised => 'Celebrar';

  @override
  String get feedJustNow => 'Ahora';

  @override
  String get feedReactionFailed => 'No se pudo guardar tu reacción';

  @override
  String get accountTitle => 'Cuenta';

  @override
  String get accountPhotoDescription => 'Foto de perfil';

  @override
  String get accountSignOut => 'Cerrar sesión';

  @override
  String get accountSignedOut => 'Sesión cerrada.';

  @override
  String get loadFailed => 'No se pudo cargar. Revisa tu conexión e inténtalo de nuevo.';

  @override
  String get retry => 'Reintentar';

  @override
  String get loading => 'Cargando…';

  @override
  String get navTrain => 'Entrenar';

  @override
  String get trainTitle => 'Entrenar';

  @override
  String trainExercisesCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count ejercicios',
      one: '$count ejercicio',
    );
    return '$_temp0';
  }

  @override
  String get trainNoRoutines => 'No hay rutinas disponibles';

  @override
  String trainInProgress(String name) {
    return 'Entrenamiento en curso: $name';
  }

  @override
  String get trainContinue => 'Continuar';

  @override
  String sessionSaved(String duration) {
    return 'Se guardó tu entrenamiento sin terminar ($duration).';
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
  String get back => 'Atrás';

  @override
  String get done => 'Hecho';

  @override
  String get cancel => 'Cancelar';

  @override
  String get pendingWrites => 'Pendiente de sincronizar';

  @override
  String get pendingWritesDescription =>
      'Tus cambios se guardan en este navegador y se enviarán cuando vuelva la conexión.';

  @override
  String get workoutTitle => 'Entrenamiento';

  @override
  String workoutExerciseProgress(int current, int total) {
    return 'Ejercicio $current de $total';
  }

  @override
  String get workoutGetReady => 'Prepárate';

  @override
  String get workoutGetReadyBanner => 'PREPÁRATE';

  @override
  String get workoutSkipExercise => 'Saltar ejercicio';

  @override
  String get workoutNextExercise => 'Siguiente ejercicio';

  @override
  String get workoutCompleteReps => 'Completar repeticiones';

  @override
  String get workoutTimerRunning => 'Cronómetro en marcha…';

  @override
  String get workoutRest => 'Descanso';

  @override
  String get workoutSeconds => 'segundos';

  @override
  String workoutReps(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count repeticiones',
      one: '$count repetición',
    );
    return '$_temp0';
  }

  @override
  String get workoutComplete => '¡Rutina completada!';

  @override
  String get workoutGreatJob => '¡Buen trabajo!';

  @override
  String get exerciseTypeTime => 'Ejercicio por tiempo';

  @override
  String get exerciseTypeReps => 'Ejercicio por repeticiones';

  @override
  String get exerciseTypeRest => 'Descanso';

  @override
  String get unknownExercise => 'Ejercicio desconocido';

  @override
  String get shareInGroups => 'Compartir en mis grupos';

  @override
  String get shareInGroupsOn => 'Tus grupos verán este entrenamiento en su actividad.';

  @override
  String get shareInGroupsOff => 'Este entrenamiento queda en privado.';

  @override
  String get shareIntroTitle => 'Comparte tus entrenamientos con tus grupos';

  @override
  String get shareIntroText =>
      'Cuando terminas un entrenamiento, las personas de tus grupos lo ven en la actividad del grupo: tu nombre y foto, el título del entrenamiento y su duración. Las notas y la intensidad nunca se comparten. Puedes dejar de compartir cualquier entrenamiento.';

  @override
  String get shareIntroOk => 'Entendido';

  @override
  String get shareIntroDontShare => 'No compartir este';

  @override
  String get logActivity => 'Registrar actividad';

  @override
  String get logActivityTitleNew => 'Registrar una actividad';

  @override
  String get logActivityTitleEdit => 'Editar actividad';

  @override
  String get logActivityType => 'Actividad';

  @override
  String get logActivityCustomName => 'Nombre de la actividad';

  @override
  String logActivityCounter(int count, int max) {
    return '$count/$max';
  }

  @override
  String get logActivityDuration => 'Duración';

  @override
  String logActivityMinutesShort(int count) {
    String _temp0 = intl.Intl.pluralLogic(count, locale: localeName, other: '$count min', one: '$count min');
    return '$_temp0';
  }

  @override
  String get logActivityMinutesField => 'Minutos';

  @override
  String logActivityShortDurationHint(int minutes) {
    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: 'No contará para tu meta semanal (mínimo $minutes minutos)',
      one: 'No contará para tu meta semanal (mínimo $minutes minuto)',
    );
    return '$_temp0';
  }

  @override
  String get logActivityIntensity => 'Intensidad';

  @override
  String get intensityEasy => 'Suave';

  @override
  String get intensityModerate => 'Moderada';

  @override
  String get intensityHard => 'Intensa';

  @override
  String get intensityEasyDescription => 'Se puede hablar con normalidad';

  @override
  String get intensityModerateDescription => 'Se puede hablar con esfuerzo';

  @override
  String get intensityHardDescription => 'Cuesta hablar';

  @override
  String logActivityIntensityOption(String label, String description) {
    return '$label: $description';
  }

  @override
  String get logActivityWhen => 'Cuándo';

  @override
  String logActivityTime(String time) {
    return 'Hora: $time';
  }

  @override
  String logActivityTimeChange(String time) {
    return 'Hora: $time. Cambiar la hora';
  }

  @override
  String get logActivityNote => 'Nota (opcional)';

  @override
  String get logActivitySave => 'Guardar';

  @override
  String get logActivityDelete => 'Eliminar actividad';

  @override
  String get logActivityDeleteTitle => '¿Eliminar esta actividad?';

  @override
  String get logActivityDeleteMessage => 'Se quitará del historial y de la meta semanal.';

  @override
  String get logActivityDeleteConfirm => 'Eliminar';

  @override
  String get logActivityDiscardTitle => '¿Descartar los cambios?';

  @override
  String get logActivityDiscardMessage => 'Se perderán los cambios hechos.';

  @override
  String get logActivityDiscardConfirm => 'Descartar';

  @override
  String get logActivityKeepEditing => 'Seguir editando';

  @override
  String get logActivityErrorType => 'Elige una actividad';

  @override
  String get logActivityErrorName => 'Escribe el nombre de la actividad';

  @override
  String logActivityErrorTooLong(int max) {
    String _temp0 = intl.Intl.pluralLogic(
      max,
      locale: localeName,
      other: 'Usa como máximo $max caracteres',
      one: 'Usa como máximo $max carácter',
    );
    return '$_temp0';
  }

  @override
  String logActivityErrorDuration(int min, int max) {
    String _temp0 = intl.Intl.pluralLogic(
      max,
      locale: localeName,
      other: 'Indica entre $min y $max minutos',
      one: 'Indica entre $min y $max minuto',
    );
    return '$_temp0';
  }

  @override
  String get logActivityErrorIntensity => 'Elige una intensidad';

  @override
  String get logActivityErrorFuture => 'La hora no puede estar en el futuro';

  @override
  String logActivityErrorTooEarly(int days) {
    String _temp0 = intl.Intl.pluralLogic(
      days,
      locale: localeName,
      other: 'Elige un día de los últimos $days días',
      one: 'Elige un día de los últimos $days día',
    );
    return '$_temp0';
  }

  @override
  String get logActivitySaveFailed => 'No se pudo guardar la actividad. Inténtalo de nuevo.';

  @override
  String get logActivityNotFound => 'Esta actividad ya no existe.';

  @override
  String get weeklyGoalQuestion => '¿Cuántos entrenos por semana quieres hacer?';

  @override
  String weeklyGoalWorkoutsPerWeek(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count entrenos por semana',
      one: '$count entreno por semana',
    );
    return '$_temp0';
  }

  @override
  String get weeklyGoalFewer => 'Menos entrenos';

  @override
  String get weeklyGoalMore => 'Más entrenos';

  @override
  String get weeklyGoalAppliesFromThisWeek =>
      'El cambio se aplica desde esta semana; las semanas anteriores mantienen su meta.';

  @override
  String get weeklyGoalSave => 'Guardar';

  @override
  String get weeklyGoalSet => 'Definir meta';

  @override
  String get weeklyGoalChange => 'Cambiar meta';

  @override
  String get historyDeleteAction => 'Eliminar entrada';

  @override
  String get historyDeleteTitle => '¿Eliminar esta entrada?';

  @override
  String historyDeleteMessage(String name) {
    return 'Se quitará «$name» del historial.';
  }

  @override
  String get historyDeleteConfirm => 'Eliminar';

  @override
  String get historyEditActivity => 'Editar actividad';

  @override
  String get programsTitle => 'Mis programas';

  @override
  String get programsEmptyTitle => 'Aún no tienes programas';

  @override
  String get programsEmptyText =>
      'Crea un plan de entrenamiento de varias semanas según tu edad, peso, nivel y objetivo.';

  @override
  String get programCreate => 'Crear programa';

  @override
  String get programGoalGainMuscle => 'Ganar fuerza y músculo';

  @override
  String get programGoalLoseFat => 'Perder grasa';

  @override
  String get programLevelBeginner => 'Principiante';

  @override
  String get programLevelIntermediate => 'Intermedio';

  @override
  String get programLevelAdvanced => 'Avanzado';

  @override
  String get programLocationHome => 'Casa';

  @override
  String get programLocationGym => 'Gimnasio';

  @override
  String programCardMeta(String first, String second) {
    return '$first · $second';
  }

  @override
  String programProgress(String percent) {
    return 'Progreso: $percent %';
  }

  @override
  String get programCreateTitle => 'Nuevo programa';

  @override
  String get programAgeLabel => 'Edad';

  @override
  String get programWeightLabel => 'Peso (kg)';

  @override
  String get programHeightLabel => 'Altura (cm)';

  @override
  String get programErrorRequired => 'Obligatorio';

  @override
  String programErrorRange(int min, int max) {
    return 'Introduce un valor de $min a $max';
  }

  @override
  String programHintRange(int min, int max) {
    return 'De $min a $max';
  }

  @override
  String get programLevelLabel => 'Nivel';

  @override
  String get programGoalLabel => 'Objetivo';

  @override
  String get programLocationLabel => 'Dónde entrenas';

  @override
  String get programFocusLabel => 'Enfoque';

  @override
  String get programFocusBalanced => 'Equilibrado';

  @override
  String get programFocusBalancedDescription => 'Todo el cuerpo por igual';

  @override
  String get programFocusLowerBody => 'Tren inferior';

  @override
  String get programFocusLowerBodyDescription => 'Más glúteo y pierna';

  @override
  String get programFocusUpperBody => 'Tren superior';

  @override
  String get programFocusUpperBodyDescription => 'Más pecho, espalda, hombros y brazos';

  @override
  String get programFocusCore => 'Core y estabilidad';

  @override
  String get programFocusCoreDescription => 'Más trabajo de core y equilibrio';

  @override
  String programFocusValue(String focus) {
    return 'Enfoque: $focus';
  }

  @override
  String get programWeeksLabel => 'Semanas';

  @override
  String get programDaysLabel => 'Días por semana';

  @override
  String programDecrease(String label) {
    return 'Reducir $label';
  }

  @override
  String programIncrease(String label) {
    return 'Aumentar $label';
  }

  @override
  String programBmi(String value) {
    return 'IMC: $value';
  }

  @override
  String get programLowImpactNote =>
      'Tu plan usa ejercicios de bajo impacto: sin saltos ni carrera, y caminata en lugar de correr.';

  @override
  String get programUnderweightWarning =>
      'Tu IMC es inferior a 18,5. Considera elegir «Ganar fuerza y músculo» en lugar de «Perder grasa».';

  @override
  String get programDisclaimer => 'Este programa no sustituye el consejo de un profesional de la salud.';

  @override
  String get programPreview => 'Vista previa';

  @override
  String get programCreateButton => 'Crear';

  @override
  String get programCreateFailed => 'No se pudo crear el programa. Inténtalo de nuevo.';

  @override
  String programWeekTitle(int week) {
    return 'Semana $week';
  }

  @override
  String programWeekHeader(int week, String percent) {
    return 'Semana $week · $percent %';
  }

  @override
  String get programDeload => 'Descarga';

  @override
  String get programCompleted => 'Completado';

  @override
  String get programNotCompleted => 'No completado';

  @override
  String get programNotFound => 'Este programa ya no existe.';

  @override
  String get programWorkoutNotFound => 'Este entrenamiento ya no está disponible.';

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
    String _temp0 = intl.Intl.pluralLogic(count, locale: localeName, other: '$count semanas', one: '$count semana');
    return '$_temp0';
  }

  @override
  String programDaysPerWeek(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count días por semana',
      one: '$count día por semana',
    );
    return '$_temp0';
  }

  @override
  String programIntervals(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count intervalos',
      one: '$count intervalo',
    );
    return '$_temp0';
  }

  @override
  String programMinutes(int count) {
    String _temp0 = intl.Intl.pluralLogic(count, locale: localeName, other: '$count min', one: '$count min');
    return '$_temp0';
  }

  @override
  String programRounds(int count) {
    String _temp0 = intl.Intl.pluralLogic(count, locale: localeName, other: '$count rondas', one: '$count ronda');
    return '$_temp0';
  }

  @override
  String get programOutdoorOnAndroid => 'La carrera al aire libre está disponible en la app de Android.';

  @override
  String get programReset => 'Reiniciar';

  @override
  String get programResetTitle => '¿Reiniciar entrenamiento?';

  @override
  String get programResetText =>
      'Todos los ejercicios se marcarán como no hechos para que puedas repetir el entrenamiento. Seguirá completado en el programa.';

  @override
  String get programWorkoutCompletedTitle => '¡Entrenamiento completado!';

  @override
  String get programWorkoutCompletedText => 'Buen trabajo. Tu progreso se ha guardado.';

  @override
  String get programSummarySets => 'Series';

  @override
  String get programSummaryReps => 'Reps';

  @override
  String get programSummaryTime => 'Tiempo';

  @override
  String programSecondsShort(int seconds) {
    return '$seconds s';
  }

  @override
  String get programPerSide => 'por lado';

  @override
  String programExercisesCompleted(int done, int total) {
    String _temp0 = intl.Intl.pluralLogic(
      total,
      locale: localeName,
      other: '$done de $total ejercicios completados',
      one: '$done de $total ejercicio completado',
    );
    return '$_temp0';
  }

  @override
  String programExerciseOf(int current, int total) {
    return 'Ejercicio $current de $total';
  }

  @override
  String programSetOf(int current, int total) {
    return 'Serie $current de $total';
  }

  @override
  String get programSetDone => 'Serie hecha';

  @override
  String get programStart => 'Empezar';

  @override
  String get programGetReady => 'Prepárate';

  @override
  String get programRest => 'Descanso';

  @override
  String get programSkipRest => 'Saltar descanso';

  @override
  String programNextSet(int number) {
    return 'Siguiente: serie $number';
  }

  @override
  String get programHold => 'Mantén la posición';

  @override
  String get programLeftSide => 'Lado izquierdo';

  @override
  String get programRightSide => 'Lado derecho';

  @override
  String get programSwitchSides => 'Cambia de lado';

  @override
  String get programPerSideOrder => 'Por lado: primero el izquierdo, luego el derecho';

  @override
  String programRepsPerSide(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count repeticiones por lado',
      one: '$count repetición por lado',
    );
    return '$_temp0';
  }

  @override
  String get programExitExerciseTitle => '¿Salir del ejercicio?';

  @override
  String programExitSetsText(int done, int total) {
    String _temp0 = intl.Intl.pluralLogic(
      total,
      locale: localeName,
      other: '$done de $total series hechas. Este ejercicio no se marcará como hecho.',
      one: '$done de $total serie hecha. Este ejercicio no se marcará como hecho.',
    );
    return '$_temp0';
  }

  @override
  String get programLeave => 'Salir';

  @override
  String get programStay => 'Quedarme';

  @override
  String get programSegmentWarmUp => 'Calentamiento';

  @override
  String get programSegmentJog => 'Trote';

  @override
  String get programSegmentWalk => 'Caminar';

  @override
  String get programSegmentInclineWalk => 'Caminata en pendiente';

  @override
  String get programSegmentCoolDown => 'Vuelta a la calma';

  @override
  String programSegmentOf(int current, int total) {
    return 'Tramo $current de $total';
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
    return 'Inclinación $incline %';
  }

  @override
  String programNext(String label) {
    return 'Siguiente: $label';
  }

  @override
  String get programLastSegment => 'Último tramo';

  @override
  String programTotalRemaining(String time) {
    return 'Tiempo total restante: $time';
  }

  @override
  String get programPause => 'Pausar';

  @override
  String get programResume => 'Reanudar';

  @override
  String get programSkipSegment => 'Saltar tramo';

  @override
  String get programLeaveWorkoutTitle => '¿Salir del entrenamiento?';

  @override
  String get programLeaveWorkoutText => 'Este entrenamiento no se marcará como completado.';

  @override
  String programRoundOf(int current, int total) {
    return 'Ronda $current de $total';
  }

  @override
  String get programRoundRest => 'Descanso entre rondas';

  @override
  String get programWork => 'Trabajo';

  @override
  String programWorkRestSeconds(int work, int rest) {
    return '$work s de trabajo / $rest s de descanso';
  }

  @override
  String get programSkip => 'Saltar';

  @override
  String get memberFallbackName => 'Miembro';

  @override
  String get signOutPendingTitle => 'Cambios sin copia';

  @override
  String get signOutPendingText =>
      'Algunos cambios están guardados solo en este navegador y se perderán si cierras sesión ahora. Vuelve a conectarte para enviarlos, o cierra sesión de todos modos.';

  @override
  String get signOutAnyway => 'Cerrar sesión de todos modos';

  @override
  String get gymTitle => 'Rutinas de gimnasio';

  @override
  String get gymStartRoutine => 'Iniciar rutina';

  @override
  String get gymEmptyCatalog => 'No hay rutinas de gimnasio disponibles';

  @override
  String gymSummary(String sets, String values) {
    return '$sets · $values';
  }

  @override
  String gymSummarySets(int count) {
    return 'Series: $count';
  }

  @override
  String gymSummaryReps(String values) {
    return 'Repeticiones: $values';
  }

  @override
  String gymSummaryTime(String values) {
    return 'Tiempo: $values';
  }

  @override
  String gymSummarySeconds(int seconds) {
    return '$seconds s';
  }

  @override
  String get gymStateExpanded => 'Expandido';

  @override
  String get gymStateCollapsed => 'Contraído';

  @override
  String get gymShowExercises => 'Mostrar ejercicios';

  @override
  String get gymHideExercises => 'Ocultar ejercicios';

  @override
  String get gymStateCompleted => 'Completado';

  @override
  String get gymStateNotCompleted => 'No completado';

  @override
  String get gymResetProgressTitle => '¿Reiniciar el progreso?';

  @override
  String get gymResetAction => 'Reiniciar progreso';

  @override
  String get gymResetText =>
      'Se borrarán todas las marcas de completado de esta rutina. Esta acción no se puede deshacer.';

  @override
  String get gymReset => 'Reiniciar';

  @override
  String gymCompletedCount(int done, int total) {
    String _temp0 = intl.Intl.pluralLogic(
      total,
      locale: localeName,
      other: '$done de $total ejercicios completados',
      one: '$done de $total ejercicio completado',
    );
    return '$_temp0';
  }

  @override
  String get gymRoutineEmpty => 'Esta rutina todavía no tiene ejercicios.';

  @override
  String get gymCompletedTitle => '¡Rutina completada!';

  @override
  String get gymCompletedText => '¡Buen trabajo! Terminaste todos los ejercicios de esta rutina.';

  @override
  String get gymFinish => 'Terminar';

  @override
  String get gymRoutineNotFound => 'Rutina no encontrada';

  @override
  String gymSetOf(int current, int total) {
    return 'Serie $current de $total';
  }

  @override
  String get gymSetDone => 'Serie completada';

  @override
  String get gymStart => 'Iniciar';

  @override
  String get gymGetReady => 'Prepárate';

  @override
  String get gymRest => 'Descanso';

  @override
  String gymNextSet(int current, int total) {
    return 'Siguiente: serie $current de $total';
  }

  @override
  String get gymSkipRest => 'Saltar descanso';

  @override
  String get gymExitTitle => '¿Salir del ejercicio?';

  @override
  String gymExitText(int done, int total) {
    String _temp0 = intl.Intl.pluralLogic(
      done,
      locale: localeName,
      other:
          'Progreso: $done de $total series completadas. Si sales ahora, este progreso no se guardará y el ejercicio no se marcará como completado.',
      one:
          'Progreso: $done de $total serie completada. Si sales ahora, este progreso no se guardará y el ejercicio no se marcará como completado.',
    );
    return '$_temp0';
  }

  @override
  String get gymExitConfirm => 'Salir';

  @override
  String get gymExerciseNotFound => 'Ejercicio no encontrado';

  @override
  String get gymBrowserNote =>
      'El progreso de una rutina de gimnasio se guarda en este navegador: una rutina empezada en otro dispositivo continúa allí.';

  @override
  String get trainStartToday => 'Comenzar la rutina de hoy';

  @override
  String trainDayAvailable(String day) {
    return '$day disponible';
  }

  @override
  String get trainPrograms => 'Programas';

  @override
  String get dailySelectTitle => 'Seleccionar rutina';

  @override
  String get dailyAvailableToday => 'Disponible hoy';

  @override
  String get dailyStart => 'Iniciar';

  @override
  String dailyLockedHint(String day) {
    return 'Completa el $day para desbloquearlo';
  }

  @override
  String get dailyStateLocked => 'Bloqueado';

  @override
  String get dailyStateCompleted => 'Completado';

  @override
  String get dailyStateAvailable => 'Disponible';

  @override
  String get dailyRestartAction => 'Reiniciar días';

  @override
  String get dailyRestartTitle => '¿Reiniciar los días?';

  @override
  String dailyRestartMessage(String day) {
    return 'Todos los días se bloquearán de nuevo y empezarás desde el $day. Tu historial, tu meta semanal y tus grupos no cambian.';
  }

  @override
  String get dailyRestartConfirm => 'Reiniciar';

  @override
  String get dailyCycleComplete => '¡Completaste todos los días!';

  @override
  String dailyStartAgain(String day) {
    return 'Empezar de nuevo desde el $day';
  }

  @override
  String get photoAdd => 'Agregar foto';

  @override
  String get photoChange => 'Cambiar foto';

  @override
  String get photoRemove => 'Quitar foto';

  @override
  String get photoPrivacyNote =>
      'Las fotos se guardan solo en este navegador; tus grupos solo ven una vista previa pequeña. Se elimina la ubicación.';

  @override
  String get photoProcessing => 'Guardando foto…';

  @override
  String get photoAdded => 'Foto agregada';

  @override
  String get photoErrorUnreadable => 'No se pudo leer esta imagen. Prueba con otra.';

  @override
  String get photoErrorHeic =>
      'Este navegador no puede leer fotos HEIC. Elige una foto JPEG o PNG, o cambia el formato de la cámara a Más compatible.';

  @override
  String photoErrorTooLarge(int maxMb) {
    return 'Esta foto es demasiado grande (máximo $maxMb MB). Elige una más pequeña.';
  }

  @override
  String get photoErrorNoSpace => 'No hay espacio suficiente en este navegador para guardar la foto.';

  @override
  String get photoErrorFailed => 'No se pudo guardar la foto. Tu entreno sigue guardado.';

  @override
  String photoContentDescription(String date) {
    return 'Foto del entreno, $date';
  }

  @override
  String get photoContentDescriptionUndated => 'Foto del entreno';

  @override
  String get photoOpen => 'Abrir foto';

  @override
  String get photoClose => 'Cerrar foto';

  @override
  String get photoRemoveTitle => '¿Quitar la foto?';

  @override
  String get photoRemoveMessage => 'La foto se borrará de este navegador y de tus publicaciones en tus grupos.';

  @override
  String get photoRemoveConfirm => 'Quitar';

  @override
  String get photoDropHint => 'También puedes soltar una foto aquí.';

  @override
  String get photoOtherDevices => 'Las fotos agregadas en otro dispositivo se quedan en ese dispositivo.';

  @override
  String get installHintText => 'Agrega Tio Minuter a tu pantalla de inicio para que tus fotos se mantengan guardadas.';

  @override
  String get installHintDismiss => 'Cerrar';

  @override
  String get signInRedirectIncomplete =>
      'El inicio de sesión no terminó en este navegador. Inténtalo de nuevo; si vuelve a pasar, permite las cookies de este sitio o usa otro navegador.';

  @override
  String get groupsEmptyText => 'Crea un grupo y comparte su código, o únete a uno con el código que te pasen.';

  @override
  String get groupsCreateButton => 'Crear grupo';

  @override
  String get groupsJoinButton => 'Unirme con código';

  @override
  String get groupsPrivacyNote =>
      'Los grupos ven tu nombre, tu foto, tus entrenos de la semana, tu meta, tu racha y qué tipo de entreno hiciste cada día. Nunca duraciones, notas ni fotos.';

  @override
  String groupsMyPosition(int position) {
    return 'Esta semana: puesto $position';
  }

  @override
  String get groupsGoalRequiredTitle => 'Primero define tu meta semanal';

  @override
  String get groupsGoalRequiredText =>
      'Los grupos comparan la semana de cada persona con su propia meta, así que necesitas una antes de crear un grupo o unirte a uno.';

  @override
  String get groupsGoalRequiredAction => 'Definir meta';

  @override
  String get groupsCreateTitle => 'Crear un grupo';

  @override
  String get groupsCreateNameLabel => 'Nombre del grupo';

  @override
  String get groupsCreateNameError => 'Usa de 1 a 40 caracteres.';

  @override
  String get groupsCreateAction => 'Crear';

  @override
  String get groupsCreatedTitle => '¡Grupo creado!';

  @override
  String groupsCreatedText(String name) {
    return 'Comparte este código para que tus amistades se unan a $name.';
  }

  @override
  String get groupsOpenGroup => 'Abrir grupo';

  @override
  String get groupsCodeLabel => 'Código de invitación';

  @override
  String get groupsCopyCode => 'Copiar código';

  @override
  String get groupsShareCode => 'Compartir código';

  @override
  String get groupsCodeCopied => 'Código copiado';

  @override
  String groupsShareText(String name, String code, String app) {
    return 'Únete a mi grupo $name en $app con el código $code';
  }

  @override
  String get groupsJoinTitle => 'Unirme a un grupo';

  @override
  String get groupsJoinCodeLabel => 'Código de invitación';

  @override
  String get groupsJoinCodeHint => '8 caracteres, por ejemplo K7MPX2QA';

  @override
  String get groupsJoinCodeInvalidChars => 'Los códigos nunca usan 0, O, 1, I ni L.';

  @override
  String get groupsJoinLooking => 'Buscando el grupo…';

  @override
  String groupsJoinPreview(String name) {
    return 'Te vas a unir a $name.';
  }

  @override
  String get groupsJoinAction => 'Unirme';

  @override
  String get groupsLeave => 'Salir del grupo';

  @override
  String get groupsOwnerMenu => 'Opciones del grupo';

  @override
  String groupsMemberMenu(String name) {
    return 'Opciones para $name';
  }

  @override
  String get groupsRename => 'Cambiar nombre';

  @override
  String get groupsRegenerate => 'Nuevo código de invitación';

  @override
  String get groupsTransfer => 'Transferir el grupo';

  @override
  String get groupsDelete => 'Eliminar grupo';

  @override
  String get groupsRemove => 'Quitar del grupo';

  @override
  String get groupsMakeOwner => 'Dar la propiedad del grupo';

  @override
  String get groupsDialogRenameTitle => 'Cambiar el nombre del grupo';

  @override
  String get groupsDialogSave => 'Guardar';

  @override
  String get groupsDialogRegenerateTitle => '¿Crear un código de invitación nuevo?';

  @override
  String get groupsDialogRegenerateText =>
      'El código actual dejará de funcionar. Quienes ya están en el grupo siguen en él.';

  @override
  String get groupsDialogRegenerateConfirm => 'Código nuevo';

  @override
  String groupsDialogRemoveTitle(String name) {
    return '¿Quitar a $name?';
  }

  @override
  String get groupsDialogRemoveText => 'Saldrá del grupo. Podrá volver a unirse con el código actual.';

  @override
  String get groupsDialogRemoveConfirm => 'Quitar';

  @override
  String get groupsDialogTransferTitle => 'Elige quién tendrá el grupo';

  @override
  String get groupsDialogTransferBeforeLeaving => 'Este grupo es tuyo. Elige a quién se lo dejas antes de salir.';

  @override
  String groupsDialogTransferConfirmTitle(String name) {
    return '¿Dar el grupo a $name?';
  }

  @override
  String get groupsDialogTransferConfirmText =>
      'Podrá cambiar el nombre, gestionar a los miembros y eliminar el grupo.';

  @override
  String get groupsDialogTransferAndLeaveText => 'El grupo pasará a esa persona y tú saldrás.';

  @override
  String get groupsDialogTransferConfirm => 'Dar el grupo';

  @override
  String get groupsDialogTransferAndLeaveConfirm => 'Transferir y salir';

  @override
  String groupsDialogDeleteTitle(String name) {
    return '¿Eliminar $name?';
  }

  @override
  String get groupsDialogDeleteText => 'Todas las personas saldrán del grupo y su código dejará de funcionar.';

  @override
  String get groupsDialogDeleteNext => 'Continuar';

  @override
  String get groupsDialogDeleteConfirmTitle => 'Esto no se puede deshacer';

  @override
  String groupsDialogDeleteConfirmText(String name) {
    return '¿Eliminar $name para todos sus miembros?';
  }

  @override
  String get groupsDialogDeleteConfirm => 'Eliminar grupo';

  @override
  String groupsDialogLeaveTitle(String name) {
    return '¿Salir de $name?';
  }

  @override
  String get groupsDialogLeaveText => 'Puedes volver a unirte con el código del grupo.';

  @override
  String get groupsDialogLeaveAloneText => 'Eres la única persona del grupo, así que al salir se elimina.';

  @override
  String get groupsDialogLeaveConfirm => 'Salir';

  @override
  String get groupsMessageNoNetwork => 'Sin conexión a internet. Inténtalo cuando tengas conexión.';

  @override
  String get groupsMessageInvalidCode => 'Ese código no corresponde a ningún grupo. Revísalo e inténtalo de nuevo.';

  @override
  String get groupsMessageGroupFull => 'Ese grupo ya tiene 20 miembros.';

  @override
  String get groupsMessageAlreadyMember => 'Ya estás en ese grupo.';

  @override
  String get groupsMessageGroupLimit => 'Puedes estar en hasta 5 grupos. Sal de uno para unirte a otro.';

  @override
  String get groupsMessageGoalRequired => 'Primero define tu meta semanal.';

  @override
  String get groupsMessageInvalidName => 'Usa de 1 a 40 caracteres.';

  @override
  String get groupsMessageNotFound => 'Ya no tienes acceso a este grupo.';

  @override
  String get groupsMessageNotAllowed => 'Esa acción no está permitida.';

  @override
  String get groupsMessageFailed => 'Algo salió mal. Inténtalo de nuevo.';

  @override
  String get groupsMessageLeft => 'Saliste del grupo.';

  @override
  String get groupsMessageDeleted => 'Grupo eliminado.';

  @override
  String get groupsMessageRenamed => 'Nombre del grupo cambiado.';

  @override
  String groupsMessageCodeRegenerated(String code) {
    return 'Código nuevo: $code';
  }

  @override
  String groupsMessageMemberRemoved(String name) {
    return '$name salió del grupo.';
  }

  @override
  String groupsMessageOwnerChanged(String name) {
    return 'Ahora el grupo es de $name.';
  }

  @override
  String trainedTodayOne(String name) {
    return '$name ya cumplió hoy, te toca 💪';
  }

  @override
  String trainedTodayTwo(String first, String second) {
    return '$first y $second ya cumplieron hoy, te toca 💪';
  }

  @override
  String get trainedTodayOpen => 'Ver grupos';

  @override
  String get trainedTodayDismiss => 'Ocultar por hoy';

  @override
  String get feedDelete => 'Borrar';

  @override
  String get feedHide => 'Ocultar para mí';

  @override
  String feedPostMenu(String name) {
    return 'Opciones del entrenamiento de $name';
  }

  @override
  String get feedDialogDeleteOwnTitle => '¿Borrar tu publicación?';

  @override
  String get feedDialogDeleteOwnText =>
      'Desaparece de la actividad de este grupo. El entrenamiento sigue en tu historial.';

  @override
  String feedDialogDeleteOwnerTitle(String name) {
    return '¿Borrar la publicación de $name?';
  }

  @override
  String get feedDialogDeleteOwnerText =>
      'Desaparece de la actividad del grupo para todo el mundo. El entrenamiento sigue en su propio historial.';

  @override
  String get feedMessageHidden => 'Publicación oculta en este dispositivo';

  @override
  String get feedMessageDeleted => 'Publicación borrada';

  @override
  String get accountDelete => 'Eliminar cuenta';

  @override
  String get deleteDialogTitle => '¿Eliminar tu cuenta?';

  @override
  String get deleteContinue => 'Continuar';

  @override
  String get deleteConfirmTitle => '¿Eliminar definitivamente?';

  @override
  String get authErrorGroupsFailed => 'No se pudo salir de tus grupos, así que no se eliminó nada. Inténtalo de nuevo.';

  @override
  String get authErrorServerDataFailed =>
      'No se pudieron eliminar tus datos guardados en la nube, así que tu cuenta no se eliminó. Inténtalo de nuevo.';

  @override
  String get authErrorAccountNotDeleted =>
      'Se eliminaron tus datos guardados en la nube, pero no se pudo eliminar tu cuenta. Vuelve a intentar eliminar la cuenta o cierra sesión.';

  @override
  String get authErrorDeleteFailed => 'No se pudo eliminar la cuenta. Inténtalo de nuevo.';

  @override
  String trainedTodayMore(String first, String second, int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$first, $second y $count más ya cumplieron hoy, te toca 💪',
      one: '$first, $second y $count más ya cumplieron hoy, te toca 💪',
    );
    return '$_temp0';
  }

  @override
  String get groupsShareCopied => 'Invitación copiada: pégala en un mensaje para tus amistades.';

  @override
  String get groupsJoinCodeLength => 'Escribe los 8 caracteres del código.';

  @override
  String get groupsPostsNote => 'Tus publicaciones en un grupo se borran cuando sales de él.';

  @override
  String get deleteDialogMessageWeb =>
      'Se eliminan tu cuenta de Tio Minuter y todo lo guardado en la nube (entrenos, metas, programas, tus grupos y tus publicaciones). Primero confirmarás con Google. Lo que este navegador guarda de esta cuenta (fotos, entrenos en curso) también se elimina.';

  @override
  String get deleteConfirmMessageWeb =>
      'Esta acción no se puede deshacer. Se eliminan tu cuenta, tus datos en la nube y los de este navegador.';

  @override
  String get deleteInProgress => 'Eliminando tu cuenta…';

  @override
  String get accountDeletedWeb => 'Cuenta eliminada.';

  @override
  String get deleteWrongAccount => 'Elige la cuenta de Google con la que iniciaste sesión e inténtalo de nuevo.';
}

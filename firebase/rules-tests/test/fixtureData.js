// The valid documents of every type (docs/firestore-model.md), as plain data: the single source of
// shared/fixtures/firestore_documents.json, which the rules tests (fixtures.js) and the web's Dart
// tests both read. Edit here, then run `node scripts/export_fixtures.mjs` (CI checks the JSON is
// up to date with `--check`). Never import this from a test: tests read the exported JSON.
//
// Placeholders, resolved by each reader:
// - { $serverTimestamp: true }: serverTimestamp() when written (the Dart tests use a fixed time);
// - { $arg: 'name' }: an argument of the builder (the owner of a group, the author of a post…).

const SERVER_TIME = { $serverTimestamp: true };
const arg = (name) => ({ $arg: name });

const ENTRY_ID = '3f2c8a1e-5b7d-4c9a-8e21-0d6f4b3a9c10';
const PROGRAM_ID = '9a1b2c3d-4e5f-4a6b-8c7d-112233445566';
const WEEK = '2026-10-19';
const WORKOUT_ID = '2-1';
const INVITE_CODE = 'K7MPX2QA';

const sync = { updatedAt: SERVER_TIME, deleted: false, schemaVersion: 1 };

const gymEntry = {
  entryId: ENTRY_ID,
  dateLocal: '2026-10-20',
  completedAt: 1760990400000,
  source: 'GYM',
  refId: 'gym:64',
  title: 'Pull Day',
  durationSeconds: 2700,
  createdAt: 1760990400000,
  ...sync,
};

export const fixtureData = {
  constants: {
    ENTRY_ID,
    PROGRAM_ID,
    WEEK,
    WORKOUT_ID,
    GROUP_ID: 'G7kQ2mXv9TzR4pLw8NcB',
    OTHER_GROUP_ID: 'H3nV8sYq1WdK6bJr2MfT',
    INVITE_CODE,
    NEW_INVITE_CODE: 'R4TZ9WHN',
    POST_ID: '7d4e1f2a-9b3c-4d5e-8f60-a1b2c3d4e5f6',
  },
  documents: {
    user: {
      displayName: 'Alex Rivera',
      photoUrl: 'https://lh3.googleusercontent.com/a/photo',
      createdAt: SERVER_TIME,
      ...sync,
    },
    gymEntry,
    activityEntry: {
      ...gymEntry,
      source: 'ACTIVITY',
      refId: null,
      title: '',
      durationSeconds: 1800,
      activityType: 'RUNNING',
      intensity: 'MODERATE',
      note: 'Easy run by the river',
    },
    weeklyGoal: { effectiveFromWeek: WEEK, workoutsPerWeek: 4, ...sync },
    program: {
      programId: PROGRAM_ID,
      goal: 'GAIN_MUSCLE',
      level: 'BEGINNER',
      location: 'HOME',
      focus: 'BALANCED',
      weeks: 6,
      daysPerWeek: 3,
      age: 30,
      weightKg: 70.5,
      heightCm: 175,
      createdAt: 1760990400000,
      ...sync,
    },
    programWorkout: {
      workoutId: WORKOUT_ID,
      week: 2,
      dayIndex: 1,
      kind: 'STRENGTH',
      isDeload: false,
      payload: '{"kind":"STRENGTH","title":"FULL_BODY_A","slots":[]}',
      ...sync,
    },
    programCompletion: { workoutId: WORKOUT_ID, week: 2, dayIndex: 1, completedAt: 1760990400000, ...sync },
    // The daily routines' unlock (one document, "state"): no deleted field, never tombstoned
    dailyProgress: { lastCompletedDay: 7, cycle: 1, updatedAt: SERVER_TIME, schemaVersion: 1 },
    // A deletion: these fields plus only the doc's id field (entryId, programId…), added by the reader
    tombstone: { deleted: true, updatedAt: SERVER_TIME, schemaVersion: 1 },
    group: {
      name: 'Morning crew',
      ownerUid: arg('ownerUid'),
      inviteCode: INVITE_CODE,
      memberCount: 1,
      memberChangeUid: arg('ownerUid'),
      createdAt: SERVER_TIME,
      updatedAt: SERVER_TIME,
      schemaVersion: 1,
    },
    // A member's public summary: name, photo, the weekly numbers and the sources of each day
    member: {
      displayName: 'Alex Rivera',
      photoUrl: 'https://lh3.googleusercontent.com/a/photo',
      joinedAt: SERVER_TIME,
      inviteCode: INVITE_CODE,
      weekStartLocal: WEEK,
      workoutsThisWeek: 2,
      weeklyGoal: 4,
      streakWeeks: 3,
      daysThisWeek: { 1: ['GYM'], 3: ['DAILY', 'ACTIVITY'] },
      lastTrainedLocal: '2026-10-21',
      updatedAt: SERVER_TIME,
      schemaVersion: 1,
    },
    invite: { groupId: arg('groupId'), groupName: 'Morning crew', createdAt: SERVER_TIME, schemaVersion: 1 },
    membership: { groupId: arg('groupId'), joinedAt: SERVER_TIME, schemaVersion: 1 },
    // One counted workout as the group sees it (Wednesday of WEEK)
    post: {
      authorUid: arg('authorUid'),
      authorName: 'Alex Rivera',
      authorPhotoUrl: 'https://lh3.googleusercontent.com/a/photo',
      source: 'GYM',
      title: 'Pull Day',
      durationSeconds: 2700,
      dateLocal: '2026-10-21',
      weekStartLocal: WEEK,
      createdAt: SERVER_TIME,
      reactions: {},
      schemaVersion: 1,
    },
  },
};

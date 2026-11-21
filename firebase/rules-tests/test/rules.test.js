// firebase/firestore.rules on the Firestore emulator. Run with scripts/test_rules.sh.
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import {
  Timestamp,
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  serverTimestamp,
  setDoc,
  updateDoc,
} from 'firebase/firestore';
import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';
import {
  ENTRY_ID,
  PROGRAM_ID,
  WEEK,
  WORKOUT_ID,
  activityEntry,
  createEnvironment,
  documentTypes,
  gymEntry,
  program,
  programCompletion,
  programWorkout,
  tombstone,
  user,
  weeklyGoal,
} from './fixtures.js';

const ALICE = 'alice';
const BOB = 'bob';

let env;

beforeAll(async () => {
  env = await createEnvironment();
});

afterAll(async () => {
  await env?.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
});

const as = (uid) => env.authenticatedContext(uid).firestore();
const anonymous = () => env.unauthenticatedContext().firestore();

/** Writes [data] at [path] with the rules off (to set up reads, updates and deletes). */
async function seed(path, data) {
  await env.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), path), data);
  });
}

/** Removes [key] from a doc. */
function without(data, key) {
  const copy = { ...data };
  delete copy[key];
  return copy;
}

const entryPath = (uid = ALICE, id = ENTRY_ID) => `users/${uid}/trainingLog/${id}`;
const goalPath = (uid = ALICE, week = WEEK) => `users/${uid}/weeklyGoals/${week}`;
const programPath = (uid = ALICE, id = PROGRAM_ID) => `users/${uid}/programs/${id}`;
const workoutPath = (id = WORKOUT_ID) => `${programPath()}/workouts/${id}`;
const completionPath = (id = WORKOUT_ID) => `${programPath()}/completions/${id}`;

describe('unauthenticated', () => {
  for (const type of documentTypes(ALICE)) {
    it(`can't read, create, update or delete a ${type.name}`, async () => {
      const db = anonymous();
      await assertFails(setDoc(doc(db, type.path), type.valid()));
      await seed(type.path, type.valid());
      await assertFails(getDoc(doc(db, type.path)));
      await assertFails(updateDoc(doc(db, type.path), { ...type.update, updatedAt: serverTimestamp() }));
      await assertFails(deleteDoc(doc(db, type.path)));
    });
  }

  it("can't list a training log", async () => {
    await assertFails(getDocs(collection(anonymous(), `users/${ALICE}/trainingLog`)));
  });
});

describe('another user', () => {
  for (const type of documentTypes(ALICE)) {
    it(`can't read, create, update or delete someone else's ${type.name}`, async () => {
      const db = as(BOB);
      await assertFails(setDoc(doc(db, type.path), type.valid()));
      await seed(type.path, type.valid());
      await assertFails(getDoc(doc(db, type.path)));
      await assertFails(updateDoc(doc(db, type.path), { ...type.update, updatedAt: serverTimestamp() }));
      await assertFails(deleteDoc(doc(db, type.path)));
    });
  }

  it("can't list someone else's collections", async () => {
    const db = as(BOB);
    await assertFails(getDocs(collection(db, `users/${ALICE}/trainingLog`)));
    await assertFails(getDocs(collection(db, `users/${ALICE}/programs`)));
  });
});

describe('owner', () => {
  for (const type of documentTypes(ALICE)) {
    it(`creates, reads, updates and deletes a valid ${type.name}`, async () => {
      const db = as(ALICE);
      const ref = doc(db, type.path);
      await assertSucceeds(setDoc(ref, type.valid()));
      await assertSucceeds(getDoc(ref));
      await assertSucceeds(updateDoc(ref, { ...type.update, updatedAt: serverTimestamp() }));
      await assertSucceeds(deleteDoc(ref));
    });
  }

  it('lists its own collections', async () => {
    const db = as(ALICE);
    await assertSucceeds(getDocs(collection(db, `users/${ALICE}/trainingLog`)));
    await assertSucceeds(getDocs(collection(db, `users/${ALICE}/weeklyGoals`)));
    await assertSucceeds(getDocs(collection(db, `${programPath()}/workouts`)));
  });

  it('accepts optional fields absent or null', async () => {
    const db = as(ALICE);
    await assertSucceeds(setDoc(doc(db, entryPath()), without(without(gymEntry(), 'refId'), 'durationSeconds')));
    await assertSucceeds(setDoc(doc(db, entryPath()), gymEntry({ refId: null, durationSeconds: null, note: null })));
    await assertSucceeds(setDoc(doc(db, `users/${ALICE}`), user({ displayName: null, photoUrl: null })));
  });

  it('accepts every source and a custom activity name', async () => {
    const db = as(ALICE);
    for (const source of ['DAILY', 'GYM', 'PROGRAM']) {
      await assertSucceeds(setDoc(doc(db, entryPath()), gymEntry({ source })));
    }
    await assertSucceeds(
      setDoc(doc(db, entryPath()), activityEntry({ activityType: 'OTHER', title: 'Climbing' })),
    );
  });

  it('accepts the weekly goal limits 1 and 14', async () => {
    const db = as(ALICE);
    await assertSucceeds(setDoc(doc(db, goalPath()), weeklyGoal({ workoutsPerWeek: 1 })));
    await assertSucceeds(setDoc(doc(db, goalPath()), weeklyGoal({ workoutsPerWeek: 14 })));
  });
});

describe('tombstones', () => {
  it('the owner turns a live doc into a tombstone, which is final', async () => {
    const db = as(ALICE);
    const ref = doc(db, entryPath());
    await seed(entryPath(), gymEntry({ updatedAt: Timestamp.now() }));

    await assertSucceeds(setDoc(ref, tombstone('entryId', ENTRY_ID)));
    // A late edit from an offline device can't bring it back
    await assertFails(setDoc(ref, gymEntry()));
    await assertFails(updateDoc(ref, { deleted: false, updatedAt: serverTimestamp() }));
    // Writing the tombstone again is fine
    await assertSucceeds(setDoc(ref, tombstone('entryId', ENTRY_ID)));
  });

  it('a tombstone can be created for a doc that was never uploaded', async () => {
    const db = as(ALICE);
    await assertSucceeds(setDoc(doc(db, goalPath()), tombstone('effectiveFromWeek', WEEK)));
    await assertSucceeds(setDoc(doc(db, programPath()), tombstone('programId', PROGRAM_ID)));
  });

  it('every type with tombstones accepts one from the owner and nobody else', async () => {
    for (const type of documentTypes(ALICE).filter((t) => t.tombstone)) {
      await seed(type.path, type.valid({ updatedAt: Timestamp.now() }));
      await assertFails(setDoc(doc(as(BOB), type.path), type.tombstone()));
      await assertSucceeds(setDoc(doc(as(ALICE), type.path), type.tombstone()));
    }
  });

  it('a tombstone carries no data and must say deleted: true', async () => {
    const db = as(ALICE);
    await assertFails(setDoc(doc(db, entryPath()), { ...tombstone('entryId', ENTRY_ID), note: 'kept' }));
    await assertFails(setDoc(doc(db, entryPath()), { ...tombstone('entryId', ENTRY_ID), deleted: false }));
    await assertFails(setDoc(doc(db, entryPath()), gymEntry({ deleted: true })));
    await assertFails(setDoc(doc(db, entryPath()), tombstone('entryId', 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee')));
  });

  it('the user doc has no tombstone (deleting the account removes the tree)', async () => {
    await assertFails(setDoc(doc(as(ALICE), `users/${ALICE}`), user({ deleted: true })));
  });
});

describe('rejected writes', () => {
  const db = () => as(ALICE);
  const rejects = (path, data) => assertFails(setDoc(doc(db(), path), data));

  it('missing required field', async () => {
    await rejects(entryPath(), without(gymEntry(), 'dateLocal'));
    await rejects(entryPath(), without(gymEntry(), 'title'));
    await rejects(goalPath(), without(weeklyGoal(), 'workoutsPerWeek'));
    await rejects(programPath(), without(program(), 'focus'));
    await rejects(workoutPath(), without(programWorkout(), 'payload'));
    await rejects(completionPath(), without(programCompletion(), 'completedAt'));
    await rejects(`users/${ALICE}`, without(user(), 'createdAt'));
    for (const field of ['updatedAt', 'deleted', 'schemaVersion']) {
      await rejects(entryPath(), without(gymEntry(), field));
    }
  });

  it('extra key', async () => {
    await rejects(entryPath(), gymEntry({ photoPath: 'training_photos/x.jpg' }));
    await rejects(goalPath(), weeklyGoal({ extra: 1 }));
    await rejects(programPath(), program({ id: 7 }));
    await rejects(workoutPath(), programWorkout({ programId: PROGRAM_ID }));
    await rejects(`users/${ALICE}`, user({ email: 'alex@example.com' }));
  });

  it('wrong type', async () => {
    await rejects(entryPath(), gymEntry({ durationSeconds: '2700' }));
    await rejects(entryPath(), gymEntry({ durationSeconds: 2700.5 }));
    await rejects(entryPath(), gymEntry({ completedAt: Timestamp.now() }));
    await rejects(entryPath(), gymEntry({ title: 42 }));
    await rejects(entryPath(), gymEntry({ deleted: 'no' }));
    await rejects(goalPath(), weeklyGoal({ workoutsPerWeek: '4' }));
    await rejects(programPath(), program({ weightKg: '70' }));
    await rejects(programPath(), program({ age: 30.5 }));
    await rejects(workoutPath(), programWorkout({ isDeload: 'false' }));
    await rejects(workoutPath(), programWorkout({ payload: { kind: 'STRENGTH' } }));
  });

  it('bad enum value', async () => {
    await rejects(entryPath(), gymEntry({ source: 'FREE' }));
    await rejects(entryPath(), gymEntry({ source: 'gym' }));
    await rejects(entryPath(), activityEntry({ activityType: 'SKYDIVING' }));
    await rejects(entryPath(), activityEntry({ intensity: 'EXTREME' }));
    await rejects(programPath(), program({ goal: 'GET_STRONG' }));
    await rejects(programPath(), program({ level: 'EXPERT' }));
    await rejects(programPath(), program({ location: 'PARK' }));
    await rejects(programPath(), program({ focus: 'ARMS' }));
    await rejects(workoutPath(), programWorkout({ kind: 'SWIM' }));
  });

  it('inconsistent activity fields', async () => {
    await rejects(entryPath(), gymEntry({ activityType: 'RUNNING' }));
    await rejects(entryPath(), gymEntry({ intensity: 'EASY' }));
    await rejects(entryPath(), without(activityEntry(), 'activityType'));
    await rejects(entryPath(), without(activityEntry(), 'intensity'));
    await rejects(entryPath(), activityEntry({ durationSeconds: null }));
    await rejects(entryPath(), activityEntry({ durationSeconds: 59 }));
    await rejects(entryPath(), activityEntry({ durationSeconds: 36001 }));
    await rejects(entryPath(), activityEntry({ refId: 'gym:64' }));
    await rejects(entryPath(), activityEntry({ activityType: 'OTHER', title: 'x'.repeat(41) }));
  });

  it('bad dateLocal', async () => {
    for (const date of ['2026-13-01', '2026-10-32', '2026-1-05', '20261020', '2026/10/20', '', '2026-10-20T00:00']) {
      await rejects(entryPath(), gymEntry({ dateLocal: date }));
    }
    await rejects(goalPath('alice', '2026-10'), weeklyGoal({ effectiveFromWeek: '2026-10' }));
  });

  it('weekly goal 0 and 15', async () => {
    await rejects(goalPath(), weeklyGoal({ workoutsPerWeek: 0 }));
    await rejects(goalPath(), weeklyGoal({ workoutsPerWeek: 15 }));
  });

  it('negative duration, or longer than a Kotlin Int', async () => {
    await rejects(entryPath(), gymEntry({ durationSeconds: -1 }));
    await rejects(entryPath(), gymEntry({ durationSeconds: 2147483648 }));
    await rejects(entryPath(), activityEntry({ durationSeconds: -60 }));
  });

  it('out-of-range program profile and workout keys', async () => {
    await rejects(programPath(), program({ weeks: 3 }));
    await rejects(programPath(), program({ weeks: 13 }));
    await rejects(programPath(), program({ daysPerWeek: 6 }));
    await rejects(programPath(), program({ age: 15 }));
    await rejects(programPath(), program({ weightKg: 251 }));
    await rejects(programPath(), program({ heightCm: 119 }));
    await rejects(workoutPath('13-0'), programWorkout({ workoutId: '13-0', week: 13, dayIndex: 0 }));
    await rejects(workoutPath('1-5'), programWorkout({ workoutId: '1-5', week: 1, dayIndex: 5 }));
  });

  it('string too long', async () => {
    await rejects(entryPath(), gymEntry({ title: 'x'.repeat(501) }));
    await rejects(entryPath(), gymEntry({ refId: 'x'.repeat(201) }));
    await rejects(entryPath(), activityEntry({ note: 'x'.repeat(201) }));
    await rejects(workoutPath(), programWorkout({ payload: 'x'.repeat(200001) }));
    await rejects(`users/${ALICE}`, user({ displayName: 'x'.repeat(501) }));
    await rejects(`users/${ALICE}`, user({ photoUrl: 'http://example.com/photo' }));
  });

  it('updatedAt not equal to request.time', async () => {
    const yesterday = Timestamp.fromMillis(Date.now() - 86_400_000);
    await rejects(entryPath(), gymEntry({ updatedAt: yesterday }));
    await rejects(entryPath(), gymEntry({ updatedAt: Date.now() }));
    await rejects(goalPath(), weeklyGoal({ updatedAt: Timestamp.now() }));
    await rejects(programPath(), program({ updatedAt: yesterday }));
    await rejects(completionPath(), programCompletion({ updatedAt: yesterday }));
    await rejects(entryPath(), { ...tombstone('entryId', ENTRY_ID), updatedAt: yesterday });

    // An update that keeps the stored updatedAt is rejected too
    await seed(goalPath(), weeklyGoal({ updatedAt: yesterday }));
    await assertFails(updateDoc(doc(db(), goalPath()), { workoutsPerWeek: 5 }));
  });

  it('wrong schemaVersion', async () => {
    for (const version of [0, 2, '1', 1.5]) {
      await rejects(entryPath(), gymEntry({ schemaVersion: version }));
    }
    await rejects(programPath(), program({ schemaVersion: 2 }));
    await rejects(entryPath(), { ...tombstone('entryId', ENTRY_ID), schemaVersion: 2 });
  });

  it('path id different from the doc id', async () => {
    const otherId = '00000000-1111-4222-8333-444444444444';
    await rejects(entryPath(ALICE, otherId), gymEntry());
    await rejects(goalPath(ALICE, '2026-10-26'), weeklyGoal());
    await rejects(programPath(ALICE, otherId), program());
    await rejects(workoutPath('1-0'), programWorkout());
    await rejects(completionPath('1-0'), programCompletion());
    // workoutId must also agree with week and dayIndex
    await rejects(workoutPath(), programWorkout({ week: 3 }));
    await rejects(completionPath(), programCompletion({ dayIndex: 0 }));
  });

  it('ids that are not lowercase UUIDs', async () => {
    const upper = ENTRY_ID.toUpperCase();
    await rejects(entryPath(ALICE, upper), gymEntry({ entryId: upper }));
    await rejects(entryPath(ALICE, '42'), gymEntry({ entryId: '42' }));
    await rejects(programPath(ALICE, '7'), program({ programId: '7' }));
  });

  it('createdAt of the user doc is set once by the server', async () => {
    await rejects(`users/${ALICE}`, user({ createdAt: Timestamp.fromMillis(0) }));
    await seed(`users/${ALICE}`, user({ createdAt: Timestamp.fromMillis(1_000), updatedAt: Timestamp.now() }));
    await assertFails(
      updateDoc(doc(db(), `users/${ALICE}`), { createdAt: serverTimestamp(), updatedAt: serverTimestamp() }),
    );
  });
});

describe('natural ids come back after a deletion', () => {
  it('set goal → delete → set again for the same week', async () => {
    const ref = doc(as(ALICE), goalPath());
    await assertSucceeds(setDoc(ref, weeklyGoal({ workoutsPerWeek: 3 })));
    await assertSucceeds(setDoc(ref, tombstone('effectiveFromWeek', WEEK)));
    await assertSucceeds(setDoc(ref, weeklyGoal({ workoutsPerWeek: 5 })));
    await assertSucceeds(updateDoc(ref, { workoutsPerWeek: 6, updatedAt: serverTimestamp() }));
  });

  it('a program workout and its completion can be written again after a tombstone', async () => {
    const db = as(ALICE);
    for (const [path, valid] of [
      [workoutPath(), programWorkout],
      [completionPath(), programCompletion],
    ]) {
      await assertSucceeds(setDoc(doc(db, path), valid()));
      await assertSucceeds(setDoc(doc(db, path), tombstone('workoutId', WORKOUT_ID)));
      await assertSucceeds(setDoc(doc(db, path), valid()));
    }
  });

  it('only the owner revives, and only with a valid doc', async () => {
    await seed(goalPath(), { ...tombstone('effectiveFromWeek', WEEK), updatedAt: Timestamp.now() });
    await assertFails(setDoc(doc(as(BOB), goalPath()), weeklyGoal()));
    await assertFails(setDoc(doc(as(ALICE), goalPath()), weeklyGoal({ workoutsPerWeek: 0 })));
    await assertFails(setDoc(doc(as(ALICE), goalPath()), weeklyGoal({ deleted: true })));
    await assertSucceeds(setDoc(doc(as(ALICE), goalPath()), weeklyGoal()));
  });

  it('random ids (entries, programs) stay deleted', async () => {
    const db = as(ALICE);
    await seed(programPath(), { ...tombstone('programId', PROGRAM_ID), updatedAt: Timestamp.now() });
    await assertFails(setDoc(doc(db, programPath()), program()));
    await seed(entryPath(), { ...tombstone('entryId', ENTRY_ID), updatedAt: Timestamp.now() });
    await assertFails(setDoc(doc(db, entryPath()), activityEntry()));
  });
});

// Every value the app can produce is accepted (docs/firestore-model.md, "App constraints")
describe('edge values the app allows', () => {
  const db = () => as(ALICE);
  const accepts = (path, data) => assertSucceeds(setDoc(doc(db(), path), data));
  // Kotlin String.length counts UTF-16 units: 40 of them can be 20 emoji or 40 accented letters
  const NAMES_OF_40 = ['x'.repeat(40), 'ñ'.repeat(40), '💪'.repeat(20), 'Ñandú 🏃'.repeat(5)];

  it('activity duration 1 and 600 minutes (the UI limits)', async () => {
    await accepts(entryPath(), activityEntry({ durationSeconds: 60 }));
    await accepts(entryPath(), activityEntry({ durationSeconds: 36000 }));
  });

  it('custom activity name of 40 characters, with accents or emoji', async () => {
    for (const title of NAMES_OF_40) {
      await accepts(entryPath(), activityEntry({ activityType: 'OTHER', title }));
    }
  });

  it('note of 200 characters, with accents or emoji', async () => {
    // Kotlin length 200 each ('🏋️' would be 3 UTF-16 units: 66 of them fit)
    for (const note of ['x'.repeat(200), 'é'.repeat(200), '💪'.repeat(100), '🏋️'.repeat(66)]) {
      await accepts(entryPath(), activityEntry({ note }));
    }
  });

  it('every activity type and intensity', async () => {
    const types = ['OWN_GYM_ROUTINE', 'RUNNING', 'WALKING', 'CYCLING', 'SWIMMING', 'YOGA', 'PILATES', 'DANCE',
      'TEAM_SPORT', 'RACKET_SPORT', 'MARTIAL_ARTS', 'HIIT', 'STRETCHING', 'OTHER'];
    for (const activityType of types) {
      await accepts(entryPath(), activityEntry({ activityType, title: activityType === 'OTHER' ? 'Climbing' : '' }));
    }
    for (const intensity of ['EASY', 'MODERATE', 'HARD']) {
      await accepts(entryPath(), activityEntry({ intensity }));
    }
  });

  it('null duration on DAILY, GYM and PROGRAM entries (fewer than two completion times)', async () => {
    for (const source of ['DAILY', 'GYM', 'PROGRAM']) {
      await accepts(entryPath(), gymEntry({ source, durationSeconds: null }));
    }
  });

  it('automatic entries longer than a day (gym progress kept across days, a workout left open)', async () => {
    await accepts(entryPath(), gymEntry({ durationSeconds: 0 }));
    await accepts(entryPath(), gymEntry({ durationSeconds: 30 * 86400 }));
    await accepts(entryPath(), gymEntry({ source: 'DAILY', durationSeconds: 2147483647 }));
  });

  it('an entry written before activities existed (v3): no activity fields, note or refId', async () => {
    const old = gymEntry({ source: 'DAILY', refId: 'daily:1', title: 'Day 1' });
    for (const key of ['refId', 'durationSeconds', 'activityType', 'intensity', 'note']) delete old[key];
    await accepts(entryPath(), old);
  });

  it('empty and long titles, long refIds', async () => {
    await accepts(entryPath(), gymEntry({ title: '' }));
    await accepts(entryPath(), gymEntry({ title: 'Día 3 · ' + 'x'.repeat(492) }));
    await accepts(entryPath(), gymEntry({ source: 'PROGRAM', refId: `program:${PROGRAM_ID}:12:4` }));
  });

  it('any device clock value for completedAt and createdAt', async () => {
    await accepts(entryPath(), gymEntry({ completedAt: 0, createdAt: 0 }));
    await accepts(entryPath(), gymEntry({ completedAt: -86400000, createdAt: 253402300800000 }));
    await accepts(completionPath(), programCompletion({ completedAt: 0 }));
    await accepts(programPath(), program({ createdAt: 0 }));
  });

  it('dates with a five-digit year (a device clock past 9999)', async () => {
    await accepts(entryPath(), gymEntry({ dateLocal: '10000-01-01' }));
  });

  it('weekly goal history: every value 1-14 (older daysPerWeek values are 1-7)', async () => {
    for (let workoutsPerWeek = 1; workoutsPerWeek <= 14; workoutsPerWeek++) {
      await accepts(goalPath(), weeklyGoal({ workoutsPerWeek }));
    }
  });

  it('program profile limits and decimal weights', async () => {
    const edges = [
      { age: 16, weightKg: 30, heightCm: 120, weeks: 4, daysPerWeek: 2 },
      { age: 90, weightKg: 250, heightCm: 230, weeks: 12, daysPerWeek: 5 },
      { weightKg: 30.0001 },
      { weightKg: 72.35 },
    ];
    for (const edge of edges) await accepts(programPath(), program(edge));
    for (const focus of ['BALANCED', 'LOWER_BODY', 'UPPER_BODY', 'CORE']) await accepts(programPath(), program({ focus }));
  });

  it('the last workout of the longest program (week 12, day index 4) and every kind', async () => {
    await accepts(workoutPath('12-4'), programWorkout({ workoutId: '12-4', week: 12, dayIndex: 4 }));
    await accepts(completionPath('12-4'), programCompletion({ workoutId: '12-4', week: 12, dayIndex: 4 }));
    for (const kind of ['STRENGTH', 'OUTDOOR_RUN', 'TREADMILL', 'HOME_CIRCUIT']) {
      await accepts(workoutPath(), programWorkout({ kind, isDeload: true }));
    }
  });

  it('a large workout payload', async () => {
    await accepts(workoutPath(), programWorkout({ payload: 'x'.repeat(200000) }));
  });

  it('a user without a Google name or photo, or with a long name', async () => {
    await accepts(`users/${ALICE}`, user({ displayName: null, photoUrl: null }));
    await assertSucceeds(deleteDoc(doc(db(), `users/${ALICE}`)));
    await accepts(`users/${ALICE}`, user({ displayName: 'Ñ'.repeat(500) }));
  });
});

describe('paths outside users/{uid}', () => {
  for (const path of [
    'groups/g1',
    'feed/item1',
    'catalog/exercises',
    `trainingLog/${ENTRY_ID}`,
    `users/${ALICE}/settings/weeklyGoal`,
    `users/${ALICE}/gymProgress/64-0`,
    `users/${ALICE}/trainingLog/${ENTRY_ID}/photos/p1`,
  ]) {
    it(`denies ${path} to the signed-in owner and to anyone`, async () => {
      await assertFails(setDoc(doc(as(ALICE), path), { value: 1 }));
      await assertFails(getDoc(doc(as(ALICE), path)));
      await assertFails(getDoc(doc(anonymous(), path)));
    });
  }

  it('denies listing the users collection', async () => {
    await assertFails(getDocs(collection(as(ALICE), 'users')));
  });
});

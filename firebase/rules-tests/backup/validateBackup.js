// Maps a phone backup (tiominuter.db, programs.db) to Firestore docs with the mapping of
// docs/firestore-model.md and writes each one as its owner into the Firestore emulator.
// Prints counts and field names only: never a value from the backup.
import { execFileSync } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { existsSync, mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { DatabaseSync } from 'node:sqlite';
import { assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, serverTimestamp, setDoc } from 'firebase/firestore';
import {
  activityEntry,
  createEnvironment,
  gymEntry,
  program,
  programCompletion,
  programWorkout,
  user,
  weeklyGoal,
} from '../test/fixtures.js';

const OWNER = 'backup-owner';

/** What each field must be, as the rules say (shown for a rejected field). */
const RULES = {
  entryId: 'lowercase UUID equal to the path id',
  dateLocal: 'YYYY-MM-DD',
  completedAt: 'int',
  createdAt: 'int',
  source: 'DAILY | GYM | PROGRAM | ACTIVITY',
  title: 'string ≤ 500 (ACTIVITY ≤ 40)',
  refId: 'string ≤ 200 or null (ACTIVITY: null)',
  durationSeconds: 'int 0-2147483647 or null (ACTIVITY: required, 60-36000)',
  activityType: 'ActivityType code (ACTIVITY), null otherwise',
  intensity: 'EASY | MODERATE | HARD (ACTIVITY), null otherwise',
  note: 'string ≤ 200 or null',
  effectiveFromWeek: 'YYYY-MM-DD equal to the path id',
  workoutsPerWeek: 'int 1-14',
  programId: 'lowercase UUID equal to the path id',
  goal: 'GAIN_MUSCLE | LOSE_FAT',
  level: 'BEGINNER | INTERMEDIATE | ADVANCED',
  location: 'HOME | GYM',
  focus: 'BALANCED | LOWER_BODY | UPPER_BODY | CORE',
  weeks: 'int 4-12',
  daysPerWeek: 'int 2-5',
  age: 'int 16-90',
  weightKg: 'number 30-250',
  heightCm: 'int 120-230',
  workoutId: '"{week}-{dayIndex}" equal to the path id',
  week: 'int 1-12',
  dayIndex: 'int 0-4',
  kind: 'STRENGTH | OUTDOOR_RUN | TREADMILL | HOME_CIRCUIT',
  isDeload: 'bool',
  payload: 'string of 2-200000 characters',
};

/** Extracts only databases/ into a temp dir outside the repo and checkpoints the WAL there. */
function extractDatabases(tarPath) {
  const dir = mkdtempSync(join(tmpdir(), 'tiominuter-backup-'));
  execFileSync('tar', ['-xf', tarPath, '-C', dir, 'databases']);
  const databases = join(dir, 'databases');
  for (const name of ['tiominuter.db', 'programs.db']) {
    if (!existsSync(join(databases, name))) throw new Error(`The backup has no databases/${name}`);
    // Write access only to the temp copy: fold the WAL into the main file
    const db = new DatabaseSync(join(databases, name));
    db.exec('PRAGMA wal_checkpoint(TRUNCATE)');
    db.close();
  }
  return { dir, databases };
}

function readRows(file, sql) {
  const db = new DatabaseSync(file, { readOnly: true });
  try {
    return db.prepare(sql).all();
  } finally {
    db.close();
  }
}

const sync = () => ({ updatedAt: serverTimestamp(), deleted: false, schemaVersion: 1 });
const nullable = (v) => (v === undefined ? null : v);

/** Room → Firestore, exactly as docs/firestore-model.md describes (Phase 2b's mapper). */
function mapBackup(databases) {
  const appDb = join(databases, 'tiominuter.db');
  const programsDb = join(databases, 'programs.db');
  const docs = [];
  const notes = { programRefIdsToMissingPrograms: 0 };

  // Room's program ids are device-local: a new UUID per program, as Phase 2b's migration will add
  const programUuids = new Map();
  const programUuid = (localId) => {
    if (!programUuids.has(localId)) programUuids.set(localId, randomUUID());
    return programUuids.get(localId);
  };

  const programs = readRows(programsDb, 'SELECT * FROM programs');
  for (const row of programs) {
    const programId = programUuid(row.id);
    docs.push({
      collection: 'programs',
      path: `users/${OWNER}/programs/${programId}`,
      valid: program,
      data: {
        programId,
        goal: row.goal,
        level: row.level,
        location: row.location,
        focus: row.focus,
        weeks: row.weeks,
        daysPerWeek: row.daysPerWeek,
        age: row.age,
        weightKg: row.weightKg,
        heightCm: row.heightCm,
        createdAt: row.createdAt,
        ...sync(),
      },
    });
  }
  const knownPrograms = new Set(programs.map((row) => row.id));

  for (const row of readRows(programsDb, 'SELECT * FROM program_workouts')) {
    const workoutId = `${row.week}-${row.dayIndex}`;
    docs.push({
      collection: 'programs/*/workouts',
      path: `users/${OWNER}/programs/${programUuid(row.programId)}/workouts/${workoutId}`,
      valid: programWorkout,
      data: {
        workoutId,
        week: row.week,
        dayIndex: row.dayIndex,
        kind: row.kind,
        isDeload: row.isDeload === 1,
        payload: row.payload,
        ...sync(),
      },
    });
  }

  for (const row of readRows(programsDb, 'SELECT * FROM program_workout_progress')) {
    const workoutId = `${row.week}-${row.dayIndex}`;
    docs.push({
      collection: 'programs/*/completions',
      path: `users/${OWNER}/programs/${programUuid(row.programId)}/completions/${workoutId}`,
      valid: programCompletion,
      data: { workoutId, week: row.week, dayIndex: row.dayIndex, completedAt: row.completedAt, ...sync() },
    });
  }

  for (const row of readRows(appDb, 'SELECT * FROM training_log')) {
    let refId = nullable(row.refId);
    const programRef = typeof refId === 'string' ? refId.match(/^program:(\d+):(\d+):(\d+)$/) : null;
    if (programRef) {
      const localId = Number(programRef[1]);
      if (!knownPrograms.has(localId)) notes.programRefIdsToMissingPrograms++;
      refId = `program:${programUuid(localId)}:${programRef[2]}:${programRef[3]}`;
    }
    docs.push({
      collection: 'trainingLog',
      path: `users/${OWNER}/trainingLog/${row.uuid}`,
      valid: row.source === 'ACTIVITY' ? activityEntry : gymEntry,
      data: {
        entryId: row.uuid,
        dateLocal: row.dateLocal,
        completedAt: row.completedAt,
        source: row.source,
        refId,
        title: row.title,
        durationSeconds: nullable(row.durationSeconds),
        activityType: nullable(row.activityType),
        intensity: nullable(row.intensity),
        note: nullable(row.note),
        createdAt: row.createdAt,
        ...sync(),
      },
    });
  }

  for (const row of readRows(appDb, 'SELECT * FROM weekly_goal')) {
    docs.push({
      collection: 'weeklyGoals',
      path: `users/${OWNER}/weeklyGoals/${row.effectiveFromWeek}`,
      valid: weeklyGoal,
      data: { effectiveFromWeek: row.effectiveFromWeek, workoutsPerWeek: row.workoutsPerWeek, ...sync() },
    });
  }

  // The profile has no Room table: the doc the app will write, without the Google name or photo
  docs.push({
    collection: 'users (profile)',
    path: `users/${OWNER}`,
    valid: user,
    data: { displayName: null, photoUrl: null, createdAt: serverTimestamp(), ...sync() },
  });

  const notSynced = {
    gym_exercise_progress: readRows(appDb, 'SELECT COUNT(*) AS n FROM gym_exercise_progress')[0].n,
    program_slot_progress: readRows(programsDb, 'SELECT COUNT(*) AS n FROM program_slot_progress')[0].n,
    'training_log.photoPath (set)': readRows(appDb, 'SELECT COUNT(*) AS n FROM training_log WHERE photoPath IS NOT NULL')[0].n,
  };
  const largestPayload = Math.max(0, ...docs.filter((d) => d.data.payload).map((d) => d.data.payload.length));
  return { docs, notSynced, notes, largestPayload };
}

/** Which fields made the rules reject [entry]: each one is swapped for a valid value and retried. */
async function rejectedFields(db, entry) {
  const reference = entry.valid();
  const keys = Object.keys(entry.data);
  const fields = [];
  for (const key of Object.keys(reference)) {
    if (!keys.includes(key)) fields.push(`${key} (missing)`);
  }
  for (const key of keys) {
    if (!(key in reference) && !['refId', 'durationSeconds', 'activityType', 'intensity', 'note', 'displayName', 'photoUrl'].includes(key)) {
      fields.push(`${key} (not allowed)`);
      continue;
    }
    // Keep the path id fields: swapping them would only move the doc
    if (['entryId', 'programId', 'workoutId', 'effectiveFromWeek'].includes(key)) continue;
    const candidate = { ...entry.data, [key]: key in reference ? reference[key] : null };
    try {
      await setDoc(doc(db, entry.path), candidate);
      fields.push(key);
    } catch {
      // Not this field alone
    }
  }
  return fields.length ? fields : ['(several fields together, or the path id)'];
}

export async function validateBackup(tarPath) {
  const { dir, databases } = extractDatabases(tarPath);
  let env;
  try {
    const { docs, notSynced, notes, largestPayload } = mapBackup(databases);
    env = await createEnvironment();
    await env.clearFirestore();
    const db = env.authenticatedContext(OWNER).firestore();

    const counts = new Map();
    const rejections = new Map();
    for (const entry of docs) {
      const count = counts.get(entry.collection) ?? { total: 0, accepted: 0, rejected: 0 };
      counts.set(entry.collection, count);
      count.total++;
      try {
        await assertSucceeds(setDoc(doc(db, entry.path), entry.data));
        count.accepted++;
      } catch {
        count.rejected++;
        for (const field of await rejectedFields(db, entry)) {
          const key = `${entry.collection} · ${field}`;
          rejections.set(key, (rejections.get(key) ?? 0) + 1);
        }
      }
    }

    console.log('\nBackup → Firestore rules (counts only, no personal values)\n');
    console.log('collection'.padEnd(26) + 'docs'.padStart(6) + 'accepted'.padStart(10) + 'rejected'.padStart(10));
    let total = 0;
    let rejected = 0;
    for (const [collection, c] of counts) {
      console.log(collection.padEnd(26) + String(c.total).padStart(6) + String(c.accepted).padStart(10) + String(c.rejected).padStart(10));
      total += c.total;
      rejected += c.rejected;
    }
    console.log('total'.padEnd(26) + String(total).padStart(6) + String(total - rejected).padStart(10) + String(rejected).padStart(10));
    console.log('\nNot synced (by design):');
    for (const [name, n] of Object.entries(notSynced)) console.log(`  ${name}: ${n} rows`);
    console.log(`PROGRAM refIds pointing to a deleted program (mapped to a new UUID): ${notes.programRefIdsToMissingPrograms}`);
    console.log(`Largest workout payload: ${largestPayload} characters (rule: ≤ 200000)`);

    if (rejections.size) {
      console.log('\nRejections (collection · field: rule):');
      for (const [key, n] of rejections) {
        const field = key.split(' · ')[1].split(' ')[0];
        console.log(`  ${key}: ${n}${RULES[field] ? `  [rule: ${RULES[field]}]` : ''}`);
      }
    }
    return rejected;
  } finally {
    await env?.cleanup();
    rmSync(dir, { recursive: true, force: true });
    if (existsSync(dir)) throw new Error(`Could not delete ${dir}`);
  }
}

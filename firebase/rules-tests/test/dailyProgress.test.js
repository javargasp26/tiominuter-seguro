// users/{uid}/dailyProgress/state (the daily routines' unlock) on the Firestore emulator.
// Run with scripts/test_rules.sh. The generic owner / other user / anonymous cases are in
// rules.test.js (documentTypes); these are the document's own fields and edge values.
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { Timestamp, collection, deleteDoc, doc, getDoc, getDocs, setDoc, updateDoc, serverTimestamp } from 'firebase/firestore';
import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';
import { createEnvironment, dailyProgress, tombstone } from './fixtures.js';

const ALICE = 'alice';
const BOB = 'bob';
const PATH = `users/${ALICE}/dailyProgress/state`;

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
const accepts = (data, path = PATH) => assertSucceeds(setDoc(doc(as(ALICE), path), data));
const rejects = (data, path = PATH) => assertFails(setDoc(doc(as(ALICE), path), data));

function without(data, key) {
  const copy = { ...data };
  delete copy[key];
  return copy;
}

async function seed(data) {
  await env.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), PATH), data);
  });
}

describe('daily progress: valid documents', () => {
  it('the owner writes, reads, overwrites (a set, as the sync does) and deletes it', async () => {
    await accepts(dailyProgress());
    await assertSucceeds(getDoc(doc(as(ALICE), PATH)));
    await accepts(dailyProgress({ lastCompletedDay: 8 }));
    // A restart: a new cycle back at 0
    await accepts(dailyProgress({ lastCompletedDay: 0, cycle: 2 }));
    await assertSucceeds(updateDoc(doc(as(ALICE), PATH), { lastCompletedDay: 1, updatedAt: serverTimestamp() }));
    // Delete account erases it
    await assertSucceeds(deleteDoc(doc(as(ALICE), PATH)));
  });

  it('every value the app can produce', async () => {
    for (const [lastCompletedDay, cycle] of [
      [0, 0],
      [1, 0],
      [30, 0],
      [999, 0],
      [0, 1],
      [12, 41],
      [999, 2147483647],
    ]) {
      await accepts(dailyProgress({ lastCompletedDay, cycle }));
    }
  });

  it('the clients merge it, so a write going back is not refused (it would block a sync batch)', async () => {
    await seed(dailyProgress({ lastCompletedDay: 20, cycle: 3, updatedAt: Timestamp.now() }));
    await accepts(dailyProgress({ lastCompletedDay: 4, cycle: 1 }));
  });

  it('the owner lists the collection (the Android pull) and nobody else does', async () => {
    await accepts(dailyProgress());
    await assertSucceeds(getDocs(collection(as(ALICE), `users/${ALICE}/dailyProgress`)));
    await assertFails(getDocs(collection(as(BOB), `users/${ALICE}/dailyProgress`)));
    await assertFails(getDocs(collection(anonymous(), `users/${ALICE}/dailyProgress`)));
  });
});

describe('daily progress: refused', () => {
  it('days and cycles out of range', async () => {
    await rejects(dailyProgress({ lastCompletedDay: -1 }));
    await rejects(dailyProgress({ lastCompletedDay: 1000 }));
    await rejects(dailyProgress({ cycle: -1 }));
    await rejects(dailyProgress({ cycle: 2147483648 }));
  });

  it('wrong types', async () => {
    await rejects(dailyProgress({ lastCompletedDay: '7' }));
    await rejects(dailyProgress({ lastCompletedDay: 7.5 }));
    await rejects(dailyProgress({ lastCompletedDay: null }));
    await rejects(dailyProgress({ cycle: true }));
    await rejects(dailyProgress({ cycle: 1.5 }));
    await rejects(dailyProgress({ cycle: [1] }));
    await rejects(dailyProgress({ schemaVersion: '1' }));
  });

  it('missing fields', async () => {
    for (const key of ['lastCompletedDay', 'cycle', 'updatedAt', 'schemaVersion']) {
      await rejects(without(dailyProgress(), key));
    }
  });

  it('extra keys, a deleted field or a tombstone', async () => {
    await rejects(dailyProgress({ extra: 1 }));
    await rejects(dailyProgress({ deleted: false }));
    await rejects(dailyProgress({ deleted: true }));
    await rejects(dailyProgress({ id: 'state' }));
    await rejects(tombstone('id', 'state'));
    await rejects({ deleted: true, updatedAt: serverTimestamp(), schemaVersion: 1 });
  });

  it('updatedAt that is not the server time of the write', async () => {
    await rejects(dailyProgress({ updatedAt: Timestamp.now() }));
    await rejects(dailyProgress({ updatedAt: Timestamp.fromMillis(Date.now() + 60_000) }));
    await rejects(dailyProgress({ updatedAt: 1760990400000 }));
    await seed(dailyProgress({ updatedAt: Timestamp.fromMillis(1_000) }));
    await assertFails(updateDoc(doc(as(ALICE), PATH), { lastCompletedDay: 9 }));
  });

  it('another schema version', async () => {
    await rejects(dailyProgress({ schemaVersion: 2 }));
    await rejects(dailyProgress({ schemaVersion: 0 }));
  });

  it('any other document id', async () => {
    await rejects(dailyProgress(), `users/${ALICE}/dailyProgress/other`);
    await rejects(dailyProgress(), `users/${ALICE}/dailyProgress/State`);
    await rejects(dailyProgress(), `users/${ALICE}/settings/dailyProgress`);
  });

  it('someone else, a guest without account or nobody', async () => {
    await assertFails(setDoc(doc(as(BOB), PATH), dailyProgress()));
    await assertFails(setDoc(doc(anonymous(), PATH), dailyProgress()));
    await seed(dailyProgress({ updatedAt: Timestamp.now() }));
    await assertFails(getDoc(doc(as(BOB), PATH)));
    await assertFails(getDoc(doc(anonymous(), PATH)));
    await assertFails(updateDoc(doc(as(BOB), PATH), { lastCompletedDay: 9, updatedAt: serverTimestamp() }));
    await assertFails(deleteDoc(doc(as(BOB), PATH)));
    await assertFails(deleteDoc(doc(anonymous(), PATH)));
  });
});

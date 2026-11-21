// Groups in firebase/firestore.rules: every operation is a batched write the rules check as a
// whole. Run with scripts/test_rules.sh.
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import {
  collection,
  doc,
  getDoc,
  getDocs,
  increment,
  serverTimestamp,
  setDoc,
  updateDoc,
  writeBatch,
} from 'firebase/firestore';
import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import {
  GROUP_ID,
  INVITE_CODE,
  NEW_INVITE_CODE,
  OTHER_GROUP_ID,
  createEnvironment,
  group,
  groupId,
  invite,
  inviteCode,
  member,
  membership,
} from './fixtures.js';
import {
  createGroup,
  deleteGroup,
  deleteGroupCounter,
  joinGroup,
  leaveGroup,
  reconcileMembership,
  regenerateCode,
  removeMember,
  renameGroup,
  transferGroup,
} from './groupBatches.js';

const OWNER = 'owner';
const BOB = 'bob';
const CARA = 'cara';
const DANI = 'dani';
const STRANGER = 'stranger';

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
const anonymousProvider = (uid) =>
  env.authenticatedContext(uid, { firebase: { sign_in_provider: 'anonymous' } }).firestore();

/** Reads [path] with the rules off. */
async function read(path) {
  let data = null;
  await env.withSecurityRulesDisabled(async (context) => {
    const snapshot = await getDoc(doc(context.firestore(), path));
    data = snapshot.exists() ? snapshot.data() : null;
  });
  return data;
}

/** Writes [data] at [path] with the rules off. */
async function seed(path, data) {
  await env.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), path), data);
  });
}

/** OWNER's group GROUP_ID (code INVITE_CODE) with [members] joined, through the rules. */
async function groupWith(...members) {
  await assertSucceeds(createGroup(as(OWNER), OWNER, GROUP_ID, INVITE_CODE));
  for (const uid of members) {
    await assertSucceeds(joinGroup(as(uid), uid, GROUP_ID, INVITE_CODE, { summary: { displayName: uid } }));
  }
}

/** A group seeded with the rules off: [memberCount] in the group doc, [memberUids] as member docs. */
async function seedGroup(id, code, ownerUid, memberCount, memberUids) {
  await seed(`groups/${id}`, group(ownerUid, { inviteCode: code, memberCount }));
  await seed(`inviteCodes/${code}`, invite(id));
  for (const uid of memberUids) await seed(`groups/${id}/members/${uid}`, member({ inviteCode: code }));
}

describe('create a group', () => {
  it('the creator is owner and only member, with the invite code, index and counter', async () => {
    await assertSucceeds(createGroup(as(OWNER), OWNER, GROUP_ID, INVITE_CODE));

    expect((await read(`groups/${GROUP_ID}`)).memberCount).toBe(1);
    expect((await read(`inviteCodes/${INVITE_CODE}`)).groupId).toBe(GROUP_ID);
    expect((await read(`users/${OWNER}/meta/groups`)).groupCount).toBe(1);
    await assertSucceeds(getDoc(doc(as(OWNER), `groups/${GROUP_ID}`)));
  });

  it('allows 5 groups per person and denies the 6th', async () => {
    for (let n = 1; n <= 5; n++) {
      await assertSucceeds(createGroup(as(OWNER), OWNER, groupId(n), inviteCode(n)));
    }
    await assertFails(createGroup(as(OWNER), OWNER, groupId(6), inviteCode(6)));
    expect((await read(`users/${OWNER}/meta/groups`)).groupCount).toBe(5);
  });

  it('denies a group without its member doc, invite code or index', async () => {
    const db = as(OWNER);
    const withoutMember = writeBatch(db);
    withoutMember.set(doc(db, `groups/${GROUP_ID}`), group(OWNER));
    withoutMember.set(doc(db, `inviteCodes/${INVITE_CODE}`), invite(GROUP_ID));
    await assertFails(withoutMember.commit());

    const withoutInvite = writeBatch(db);
    withoutInvite.set(doc(db, `groups/${GROUP_ID}`), group(OWNER));
    withoutInvite.set(doc(db, `groups/${GROUP_ID}/members/${OWNER}`), member());
    withoutInvite.set(doc(db, `users/${OWNER}/groups/${GROUP_ID}`), membership(GROUP_ID));
    await assertFails(withoutInvite.commit());

    const withoutIndex = writeBatch(db);
    withoutIndex.set(doc(db, `groups/${GROUP_ID}`), group(OWNER));
    withoutIndex.set(doc(db, `groups/${GROUP_ID}/members/${OWNER}`), member());
    withoutIndex.set(doc(db, `inviteCodes/${INVITE_CODE}`), invite(GROUP_ID));
    await assertFails(withoutIndex.commit());
  });

  it('denies a wrong owner, member count, code, name or id', async () => {
    const attempt = (overrides, { id = GROUP_ID, code = INVITE_CODE, name = 'Morning crew' } = {}) => {
      const db = as(OWNER);
      const batch = writeBatch(db);
      batch.set(doc(db, `groups/${id}`), group(OWNER, { inviteCode: code, name, ...overrides }));
      batch.set(doc(db, `groups/${id}/members/${OWNER}`), member({ inviteCode: code }));
      batch.set(doc(db, `inviteCodes/${code}`), invite(id, { groupName: name }));
      batch.set(doc(db, `users/${OWNER}/groups/${id}`), membership(id));
      batch.set(doc(db, `users/${OWNER}/meta/groups`), {
        groupCount: 1, lastGroupId: id, updatedAt: serverTimestamp(), schemaVersion: 1,
      });
      return batch.commit();
    };

    await assertFails(attempt({ ownerUid: BOB }));
    await assertFails(attempt({ memberCount: 2 }));
    await assertFails(attempt({}, { code: 'K7MPX2QO' })); // O is ambiguous
    await assertFails(attempt({}, { code: 'K7MPX2Q' }));
    await assertFails(attempt({}, { code: 'k7mpx2qa' }));
    await assertFails(attempt({}, { name: 'x'.repeat(41) }));
    await assertFails(attempt({}, { name: '' }));
    await assertFails(attempt({}, { name: '   ' }));
    await assertFails(attempt({}, { id: 'short-id' }));
    await assertFails(attempt({ extra: true }));
    await assertSucceeds(attempt({}, { name: 'Ñ'.repeat(40) }));
  });

  it('denies guests and anonymous accounts', async () => {
    await assertFails(createGroup(anonymous(), OWNER, GROUP_ID, INVITE_CODE));
    await assertFails(createGroup(anonymousProvider(OWNER), OWNER, GROUP_ID, INVITE_CODE));
  });

  it('denies taking an invite code that is in use', async () => {
    await assertSucceeds(createGroup(as(OWNER), OWNER, GROUP_ID, INVITE_CODE));
    await assertFails(createGroup(as(BOB), BOB, OTHER_GROUP_ID, INVITE_CODE));
    expect((await read(`inviteCodes/${INVITE_CODE}`)).groupId).toBe(GROUP_ID);
  });
});

describe('join a group', () => {
  it('joins with a valid code: memberCount + 1, index and counter', async () => {
    await groupWith(BOB);

    expect((await read(`groups/${GROUP_ID}`)).memberCount).toBe(2);
    expect((await read(`users/${BOB}/groups/${GROUP_ID}`)).groupId).toBe(GROUP_ID);
    expect((await read(`users/${BOB}/meta/groups`)).groupCount).toBe(1);
    await assertSucceeds(getDoc(doc(as(BOB), `groups/${GROUP_ID}`)));
    const members = await assertSucceeds(getDocs(collection(as(BOB), `groups/${GROUP_ID}/members`)));
    expect(members.size).toBe(2);
  });

  it('a joiner can look up the exact code first', async () => {
    await groupWith();
    const found = await assertSucceeds(getDoc(doc(as(BOB), `inviteCodes/${INVITE_CODE}`)));
    expect(found.data().groupId).toBe(GROUP_ID);
    const missing = await assertSucceeds(getDoc(doc(as(BOB), `inviteCodes/${NEW_INVITE_CODE}`)));
    expect(missing.exists()).toBe(false);
  });

  it('denies the old code after a regeneration', async () => {
    await groupWith();
    await assertSucceeds(regenerateCode(as(OWNER), GROUP_ID, INVITE_CODE, NEW_INVITE_CODE));

    await assertFails(joinGroup(as(BOB), BOB, GROUP_ID, INVITE_CODE));
    await assertSucceeds(joinGroup(as(CARA), CARA, GROUP_ID, NEW_INVITE_CODE));
  });

  it('denies a wrong code, or the code of another group', async () => {
    await groupWith();
    await assertSucceeds(createGroup(as(DANI), DANI, OTHER_GROUP_ID, NEW_INVITE_CODE));

    await assertFails(joinGroup(as(BOB), BOB, GROUP_ID, 'ZZZZZZZZ'));
    await assertFails(joinGroup(as(BOB), BOB, GROUP_ID, NEW_INVITE_CODE));
  });

  it('allows the 20th member and denies the 21st', async () => {
    await seedGroup(GROUP_ID, INVITE_CODE, OWNER, 19, [OWNER]);
    await assertSucceeds(joinGroup(as(BOB), BOB, GROUP_ID, INVITE_CODE));
    expect((await read(`groups/${GROUP_ID}`)).memberCount).toBe(20);

    await assertFails(joinGroup(as(CARA), CARA, GROUP_ID, INVITE_CODE));
  });

  it('denies joining a 6th group', async () => {
    for (let n = 1; n <= 5; n++) {
      await assertSucceeds(createGroup(as(BOB), BOB, groupId(n), inviteCode(n)));
    }
    await groupWith();
    await assertFails(joinGroup(as(BOB), BOB, GROUP_ID, INVITE_CODE));
  });

  it('denies a join that skips the index, counts wrong or joins twice', async () => {
    await groupWith();
    const db = as(BOB);
    const withoutIndex = writeBatch(db);
    withoutIndex.update(doc(db, `groups/${GROUP_ID}`), {
      memberCount: increment(1), memberChangeUid: BOB, updatedAt: serverTimestamp(),
    });
    withoutIndex.set(doc(db, `groups/${GROUP_ID}/members/${BOB}`), member());
    await assertFails(withoutIndex.commit());

    await assertFails(joinGroup(db, BOB, GROUP_ID, INVITE_CODE, { groupUpdate: { memberCount: increment(2) } }));
    await assertFails(joinGroup(db, BOB, GROUP_ID, INVITE_CODE, { groupUpdate: { memberChangeUid: CARA } }));
    await assertFails(joinGroup(db, BOB, GROUP_ID, INVITE_CODE, { groupUpdate: { name: 'Taken over' } }));

    await assertSucceeds(joinGroup(db, BOB, GROUP_ID, INVITE_CODE));
    await assertFails(joinGroup(db, BOB, GROUP_ID, INVITE_CODE));
  });

  it('denies writing a member doc for someone else, or without the group update', async () => {
    await groupWith();
    await assertFails(joinGroup(as(BOB), CARA, GROUP_ID, INVITE_CODE));
    await assertFails(setDoc(doc(as(BOB), `groups/${GROUP_ID}/members/${BOB}`), member()));
  });
});

describe('who can read', () => {
  it('denies a non-member reading the group or its members', async () => {
    await groupWith(BOB);
    const db = as(STRANGER);
    await assertFails(getDoc(doc(db, `groups/${GROUP_ID}`)));
    await assertFails(getDoc(doc(db, `groups/${GROUP_ID}/members/${BOB}`)));
    await assertFails(getDocs(collection(db, `groups/${GROUP_ID}/members`)));
    await assertFails(getDoc(doc(anonymous(), `groups/${GROUP_ID}`)));
  });

  it('denies listing groups or invite codes, and code lookups without an account', async () => {
    await groupWith();
    await assertFails(getDocs(collection(as(OWNER), 'groups')));
    await assertFails(getDocs(collection(as(OWNER), 'inviteCodes')));
    await assertFails(getDoc(doc(anonymous(), `inviteCodes/${INVITE_CODE}`)));
    await assertFails(getDoc(doc(anonymousProvider(BOB), `inviteCodes/${INVITE_CODE}`)));
  });

  it("denies reading someone else's index or counter", async () => {
    await groupWith(BOB);
    await assertFails(getDoc(doc(as(OWNER), `users/${BOB}/groups/${GROUP_ID}`)));
    await assertFails(getDocs(collection(as(OWNER), `users/${BOB}/groups`)));
    await assertFails(getDoc(doc(as(OWNER), `users/${BOB}/meta/groups`)));
    await assertSucceeds(getDocs(collection(as(BOB), `users/${BOB}/groups`)));
  });

  it('a removed member reads their own (missing) member doc but not the group', async () => {
    await groupWith(BOB);
    await assertSucceeds(removeMember(as(OWNER), GROUP_ID, BOB));

    const own = await assertSucceeds(getDoc(doc(as(BOB), `groups/${GROUP_ID}/members/${BOB}`)));
    expect(own.exists()).toBe(false);
    await assertFails(getDoc(doc(as(BOB), `groups/${GROUP_ID}`)));
    await assertFails(getDocs(collection(as(BOB), `groups/${GROUP_ID}/members`)));
  });
});

describe('weekly summary', () => {
  const update = (uid, fields) => updateDoc(doc(as(uid), `groups/${GROUP_ID}/members/${uid}`), {
    updatedAt: serverTimestamp(),
    ...fields,
  });

  beforeEach(async () => {
    await groupWith(BOB);
  });

  it('a member updates their own summary', async () => {
    await assertSucceeds(update(BOB, {
      weekStartLocal: '2026-03-02',
      workoutsThisWeek: 0,
      weeklyGoal: 14,
      streakWeeks: 0,
      daysThisWeek: {},
      lastTrainedLocal: null,
      photoUrl: null,
      displayName: 'B',
    }));
    await assertSucceeds(update(BOB, {
      workoutsThisWeek: 100,
      streakWeeks: 1000,
      weeklyGoal: 1,
      daysThisWeek: { 1: ['DAILY', 'GYM', 'PROGRAM', 'ACTIVITY'], 7: [], 4: ['PROGRAM'] },
      displayName: 'Ñ'.repeat(100),
    }));
  });

  it("denies writing another member's summary, even the owner", async () => {
    await assertFails(updateDoc(
      doc(as(OWNER), `groups/${GROUP_ID}/members/${BOB}`), { workoutsThisWeek: 0, updatedAt: serverTimestamp() },
    ));
    await assertFails(updateDoc(
      doc(as(CARA), `groups/${GROUP_ID}/members/${BOB}`), { workoutsThisWeek: 0, updatedAt: serverTimestamp() },
    ));
  });

  it('denies invalid sources, days and numbers', async () => {
    for (const fields of [
      { daysThisWeek: { 1: ['RUN'] } },
      { daysThisWeek: { 1: ['GYM', 'GYM'] } },
      { daysThisWeek: { 1: ['DAILY', 'GYM', 'PROGRAM', 'ACTIVITY', 'GYM'] } },
      { daysThisWeek: { 8: ['GYM'] } },
      { daysThisWeek: { 0: ['GYM'] } },
      { daysThisWeek: { 1: 'GYM' } },
      { daysThisWeek: ['GYM'] },
      { weeklyGoal: 0 },
      { weeklyGoal: 15 },
      { weeklyGoal: null },
      { workoutsThisWeek: -1 },
      { workoutsThisWeek: 101 },
      { workoutsThisWeek: 2.5 },
      { streakWeeks: -1 },
      { streakWeeks: 1001 },
      { displayName: '' },
      { displayName: 'x'.repeat(101) },
      { photoUrl: 'http://example.com/a.png' },
    ]) {
      await assertFails(update(BOB, fields));
    }
  });

  it('denies bad dates: format, not a Monday, impossible', async () => {
    for (const fields of [
      { weekStartLocal: '2026-10-20' }, // a Tuesday
      { weekStartLocal: '2026-10-25' }, // a Sunday
      { weekStartLocal: '2026-02-30' },
      { weekStartLocal: '19-10-2026' },
      { weekStartLocal: '2026-10-19T00:00' },
      { lastTrainedLocal: '2026-13-01' },
      { lastTrainedLocal: '21/10/2026' },
    ]) {
      await assertFails(update(BOB, fields));
    }
  });

  it('denies private or extra fields, and changing the join proof', async () => {
    for (const fields of [
      { durationSeconds: 2700 },
      { note: 'Felt great' },
      { title: 'Pull Day' },
      { photoPath: 'training_photos/a.jpg' },
      { weightKg: 70 },
      { joinedAt: serverTimestamp() },
      { inviteCode: NEW_INVITE_CODE },
      { schemaVersion: 2 },
    ]) {
      await assertFails(update(BOB, fields));
    }
    await assertFails(updateDoc(doc(as(BOB), `groups/${GROUP_ID}/members/${BOB}`), { workoutsThisWeek: 3 }));
  });
});

describe('leave', () => {
  it('a member leaves: memberCount - 1, index and counter updated', async () => {
    await groupWith(BOB);
    await assertSucceeds(leaveGroup(as(BOB), BOB, GROUP_ID));

    expect((await read(`groups/${GROUP_ID}`)).memberCount).toBe(1);
    expect(await read(`users/${BOB}/groups/${GROUP_ID}`)).toBeNull();
    expect((await read(`users/${BOB}/meta/groups`)).groupCount).toBe(0);
  });

  it('denies leaving without the count or the index', async () => {
    await groupWith(BOB);
    await assertFails(leaveGroup(as(BOB), BOB, GROUP_ID, { decrement: false }));
    await assertFails(leaveGroup(as(BOB), BOB, GROUP_ID, { deleteIndex: false }));
  });

  it('denies the owner leaving while others remain', async () => {
    await groupWith(BOB);
    await assertFails(leaveGroup(as(OWNER), OWNER, GROUP_ID));
    await assertFails(leaveGroup(as(OWNER), OWNER, GROUP_ID, { newOwnerUid: STRANGER }));
  });

  it('the owner leaves by handing the group to a member in the same batch', async () => {
    await groupWith(BOB);
    await assertSucceeds(leaveGroup(as(OWNER), OWNER, GROUP_ID, { newOwnerUid: BOB }));

    const after = await read(`groups/${GROUP_ID}`);
    expect(after.ownerUid).toBe(BOB);
    expect(after.memberCount).toBe(1);
  });

  it('an owner alone leaves by deleting the group', async () => {
    await groupWith();
    await assertFails(leaveGroup(as(OWNER), OWNER, GROUP_ID));
    await assertSucceeds(deleteGroup(as(OWNER), OWNER, GROUP_ID, INVITE_CODE, [OWNER]));

    expect(await read(`groups/${GROUP_ID}`)).toBeNull();
    expect(await read(`inviteCodes/${INVITE_CODE}`)).toBeNull();
    expect((await read(`users/${OWNER}/meta/groups`)).groupCount).toBe(0);
  });
});

describe('remove a member', () => {
  it('the owner removes a member', async () => {
    await groupWith(BOB, CARA);
    await assertSucceeds(removeMember(as(OWNER), GROUP_ID, BOB));
    expect((await read(`groups/${GROUP_ID}`)).memberCount).toBe(2);
    expect(await read(`groups/${GROUP_ID}/members/${BOB}`)).toBeNull();
  });

  it('denies a non-owner, removing the owner, or skipping the count', async () => {
    await groupWith(BOB, CARA);
    await assertFails(removeMember(as(CARA), GROUP_ID, BOB));
    await assertFails(removeMember(as(OWNER), GROUP_ID, OWNER));
    await assertFails(removeMember(as(OWNER), GROUP_ID, BOB, { decrement: false }));
    await assertFails(removeMember(as(OWNER), GROUP_ID, STRANGER));
  });

  it('the removed member reconciles their stale index entry; a current member cannot', async () => {
    await groupWith(BOB, CARA);
    await assertFails(reconcileMembership(as(CARA), CARA, GROUP_ID));

    await assertSucceeds(removeMember(as(OWNER), GROUP_ID, BOB));
    await assertSucceeds(reconcileMembership(as(BOB), BOB, GROUP_ID));
    expect((await read(`users/${BOB}/meta/groups`)).groupCount).toBe(0);
  });

  it('the counter only moves together with an index entry', async () => {
    await groupWith(BOB);
    const counter = doc(as(BOB), `users/${BOB}/meta/groups`);
    await assertFails(setDoc(counter, {
      groupCount: 0, lastGroupId: GROUP_ID, updatedAt: serverTimestamp(), schemaVersion: 1,
    }));
    await assertFails(updateDoc(counter, { groupCount: increment(-1), updatedAt: serverTimestamp() }));
    await assertFails(deleteGroupCounter(as(BOB), BOB));
    await assertFails(setDoc(doc(as(BOB), `users/${BOB}/groups/${OTHER_GROUP_ID}`), membership(OTHER_GROUP_ID)));
  });
});

describe('owner actions', () => {
  it('transfers to a member; denies a non-member target and a non-owner', async () => {
    await groupWith(BOB, CARA);
    await assertFails(transferGroup(as(OWNER), GROUP_ID, STRANGER));
    await assertFails(transferGroup(as(BOB), GROUP_ID, BOB));
    await assertSucceeds(transferGroup(as(OWNER), GROUP_ID, BOB));
    expect((await read(`groups/${GROUP_ID}`)).ownerUid).toBe(BOB);

    // The old owner is now a member like any other
    await assertFails(transferGroup(as(OWNER), GROUP_ID, CARA));
    await assertSucceeds(leaveGroup(as(OWNER), OWNER, GROUP_ID));
  });

  it('renames with the invite code; denies a non-owner, a bad name, a stale invite name', async () => {
    await groupWith(BOB);
    await assertFails(renameGroup(as(BOB), GROUP_ID, INVITE_CODE, 'Evening crew'));
    await assertFails(renameGroup(as(OWNER), GROUP_ID, INVITE_CODE, 'x'.repeat(41)));
    await assertFails(renameGroup(as(OWNER), GROUP_ID, INVITE_CODE, ''));
    await assertFails(renameGroup(as(OWNER), GROUP_ID, INVITE_CODE, 'Evening crew', { updateInvite: false }));
    await assertFails(updateDoc(doc(as(OWNER), `inviteCodes/${INVITE_CODE}`), { groupName: 'Evening crew' }));

    await assertSucceeds(renameGroup(as(OWNER), GROUP_ID, INVITE_CODE, 'Evening crew'));
    expect((await read(`inviteCodes/${INVITE_CODE}`)).groupName).toBe('Evening crew');
  });

  it('regenerates the code: old invite doc deleted, new one created; denies a non-owner', async () => {
    await groupWith(BOB);
    await assertFails(regenerateCode(as(BOB), GROUP_ID, INVITE_CODE, NEW_INVITE_CODE));
    await assertFails(regenerateCode(as(OWNER), GROUP_ID, INVITE_CODE, NEW_INVITE_CODE, { deleteOld: false }));
    await assertFails(regenerateCode(as(OWNER), GROUP_ID, INVITE_CODE, NEW_INVITE_CODE, { name: 'Other' }));

    await assertSucceeds(regenerateCode(as(OWNER), GROUP_ID, INVITE_CODE, NEW_INVITE_CODE));
    expect(await read(`inviteCodes/${INVITE_CODE}`)).toBeNull();
    expect((await read(`groups/${GROUP_ID}`)).inviteCode).toBe(NEW_INVITE_CODE);
    // Members who joined with the old code stay members and can still write their summary
    await assertSucceeds(updateDoc(doc(as(BOB), `groups/${GROUP_ID}/members/${BOB}`), {
      workoutsThisWeek: 3, updatedAt: serverTimestamp(),
    }));
  });

  it('denies changing several things at once, or the creation time', async () => {
    await groupWith(BOB);
    await assertFails(updateDoc(doc(as(OWNER), `groups/${GROUP_ID}`), {
      ownerUid: BOB, name: 'Both', updatedAt: serverTimestamp(),
    }));
    await assertFails(updateDoc(doc(as(OWNER), `groups/${GROUP_ID}`), {
      createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
    }));
  });
});

describe('delete a group', () => {
  it('the owner deletes a full group (20 members) in one batch', async () => {
    const others = Array.from({ length: 19 }, (_, i) => `member${i}`);
    await seedGroup(GROUP_ID, INVITE_CODE, OWNER, 20, [OWNER, ...others]);
    await seed(`users/${OWNER}/groups/${GROUP_ID}`, membership(GROUP_ID));
    await seed(`users/${OWNER}/meta/groups`, { groupCount: 1, lastGroupId: GROUP_ID, updatedAt: new Date(), schemaVersion: 1 });

    await assertSucceeds(deleteGroup(as(OWNER), OWNER, GROUP_ID, INVITE_CODE, [OWNER, ...others]));
    expect(await read(`groups/${GROUP_ID}`)).toBeNull();
    expect(await read(`groups/${GROUP_ID}/members/member7`)).toBeNull();
  });

  it('denies a member deleting the group, and an owner keeping the invite code', async () => {
    await groupWith(BOB);
    await assertFails(deleteGroup(as(BOB), BOB, GROUP_ID, INVITE_CODE, [OWNER, BOB]));
    await assertFails(deleteGroup(as(OWNER), OWNER, GROUP_ID, INVITE_CODE, [OWNER, BOB], { deleteInvite: false }));
  });

  it('a member doc left behind is unreadable to others and its user cleans it up', async () => {
    await groupWith(BOB, CARA);
    await assertSucceeds(deleteGroup(as(OWNER), OWNER, GROUP_ID, INVITE_CODE, [OWNER, BOB]));

    await assertFails(getDocs(collection(as(CARA), `groups/${GROUP_ID}/members`)));
    await assertFails(getDoc(doc(as(CARA), `groups/${GROUP_ID}/members/${BOB}`)));
    await assertFails(updateDoc(doc(as(CARA), `groups/${GROUP_ID}/members/${CARA}`), {
      workoutsThisWeek: 3, updatedAt: serverTimestamp(),
    }));

    // Cara's reconciliation: the group is gone, so her member doc and index entry go too
    const db = as(CARA);
    const batch = writeBatch(db);
    batch.delete(doc(db, `groups/${GROUP_ID}/members/${CARA}`));
    batch.delete(doc(db, `users/${CARA}/groups/${GROUP_ID}`));
    batch.set(doc(db, `users/${CARA}/meta/groups`), {
      groupCount: increment(-1), lastGroupId: GROUP_ID, updatedAt: serverTimestamp(), schemaVersion: 1,
    }, { merge: true });
    await assertSucceeds(batch.commit());
    await assertSucceeds(reconcileMembership(as(BOB), BOB, GROUP_ID));
  });
});

describe('delete-account cleanup', () => {
  it('leaves every group, hands owned groups to the oldest member, deletes solo groups', async () => {
    const G_MEMBER = groupId(1); // DANI is a member
    const G_SHARED = groupId(2); // DANI owns it with BOB (oldest) and CARA
    const G_SOLO = groupId(3); // DANI owns it alone
    const G_GONE = groupId(4); // DANI was removed from it
    await assertSucceeds(createGroup(as(OWNER), OWNER, G_MEMBER, inviteCode(1)));
    await assertSucceeds(joinGroup(as(DANI), DANI, G_MEMBER, inviteCode(1)));
    await assertSucceeds(createGroup(as(DANI), DANI, G_SHARED, inviteCode(2)));
    await assertSucceeds(joinGroup(as(BOB), BOB, G_SHARED, inviteCode(2)));
    await assertSucceeds(joinGroup(as(CARA), CARA, G_SHARED, inviteCode(2)));
    await assertSucceeds(createGroup(as(DANI), DANI, G_SOLO, inviteCode(3)));
    await assertSucceeds(createGroup(as(OWNER), OWNER, G_GONE, inviteCode(4)));
    await assertSucceeds(joinGroup(as(DANI), DANI, G_GONE, inviteCode(4)));
    await assertSucceeds(removeMember(as(OWNER), G_GONE, DANI));
    expect((await read(`users/${DANI}/meta/groups`)).groupCount).toBe(4);

    const db = as(DANI);
    // One batch per group, in any order
    await assertSucceeds(reconcileMembership(db, DANI, G_GONE));
    await assertSucceeds(leaveGroup(db, DANI, G_MEMBER));
    await assertSucceeds(leaveGroup(db, DANI, G_SHARED, { newOwnerUid: BOB }));
    await assertSucceeds(deleteGroup(db, DANI, G_SOLO, inviteCode(3), [DANI]));
    await assertSucceeds(deleteGroupCounter(db, DANI));

    expect(await read(`users/${DANI}/meta/groups`)).toBeNull();
    const shared = await read(`groups/${G_SHARED}`);
    expect(shared.ownerUid).toBe(BOB);
    expect(shared.memberCount).toBe(2);
    expect((await read(`groups/${G_MEMBER}`)).memberCount).toBe(1);
    expect(await read(`groups/${G_SOLO}`)).toBeNull();
    expect(await read(`inviteCodes/${inviteCode(3)}`)).toBeNull();
    const remaining = await assertSucceeds(getDocs(collection(as(CARA), `groups/${G_SHARED}/members`)));
    expect(remaining.docs.map((d) => d.id).sort()).toEqual([BOB, CARA]);
  });
});

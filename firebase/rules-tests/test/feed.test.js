// The group feed in firebase/firestore.rules: posts, reactions, thumbnails, deletion and expiry.
// Run with scripts/test_rules.sh.
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import {
  Timestamp,
  collection,
  deleteField,
  doc,
  getDoc,
  getDocs,
  serverTimestamp,
  setDoc,
  updateDoc,
} from 'firebase/firestore';
import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import {
  GROUP_ID,
  INVITE_CODE,
  OTHER_GROUP_ID,
  NEW_INVITE_CODE,
  POST_ID,
  createEnvironment,
  groupId,
  inviteCode,
  post,
  thumbBytes,
} from './fixtures.js';
import { createGroup, deleteGroup, deleteGroupCounter, joinGroup, leaveGroup, removeMember } from './groupBatches.js';
import {
  POST_LIFETIME_MILLIS,
  cleanUpExpired,
  createPost,
  deleteAllPosts,
  deletePost,
  deletePostsBy,
  react,
  setThumb,
  weekFeed,
} from './feedOps.js';

const OWNER = 'owner';
const BOB = 'bob';
const CARA = 'cara';
const DANI = 'dani';
const STRANGER = 'stranger';
const DAY = 24 * 60 * 60 * 1000;

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

async function read(path) {
  let data = null;
  await env.withSecurityRulesDisabled(async (context) => {
    const snapshot = await getDoc(doc(context.firestore(), path));
    data = snapshot.exists() ? snapshot.data() : null;
  });
  return data;
}

async function seed(path, data) {
  await env.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), path), data);
  });
}

/** OWNER's group GROUP_ID with [members] joined, through the rules. */
async function groupWith(...members) {
  await assertSucceeds(createGroup(as(OWNER), OWNER, GROUP_ID, INVITE_CODE));
  for (const uid of members) await assertSucceeds(joinGroup(as(uid), uid, GROUP_ID, INVITE_CODE));
}

/** A random lowercase UUID (a training entry's uuid). */
const uuid = () => crypto.randomUUID();

/** A post of [authorUid] created [ageMillis] ago, seeded with the rules off (to test expiry). */
function seedPostCreatedAgo(postId, authorUid, ageMillis, groupIdValue = GROUP_ID) {
  return seed(
    `groups/${groupIdValue}/posts/${postId}`,
    post(authorUid, { createdAt: Timestamp.fromMillis(Date.now() - ageMillis) }),
  );
}

const postPath = (postId = POST_ID, g = GROUP_ID) => `groups/${g}/posts/${postId}`;

describe('create a post', () => {
  beforeEach(async () => {
    await groupWith(BOB);
  });

  it('a member posts a valid workout, with and without a thumbnail', async () => {
    await assertSucceeds(createPost(as(BOB), GROUP_ID, POST_ID, BOB));
    await assertSucceeds(createPost(as(BOB), GROUP_ID, uuid(), BOB, { thumb: thumbBytes(65536) }));
    await assertSucceeds(createPost(as(OWNER), GROUP_ID, uuid(), OWNER, { thumb: null }));

    const stored = await read(postPath());
    expect(stored.authorUid).toBe(BOB);
    expect(stored.createdAt).toBeInstanceOf(Timestamp);
  });

  it('accepts every value the app can produce', async () => {
    for (const overrides of [
      { durationSeconds: null },
      { durationSeconds: 0 },
      { durationSeconds: 2147483647 },
      { authorPhotoUrl: null },
      { title: '' },
      { title: 'Ñ'.repeat(500) },
      { authorName: 'Ñ'.repeat(100) },
      { authorName: 'A' },
      { source: 'DAILY' },
      { source: 'PROGRAM' },
      { source: 'ACTIVITY', title: '', activityType: 'RUNNING', durationSeconds: 1800 },
      { source: 'ACTIVITY', title: 'Climbing', activityType: 'OTHER' },
      { source: 'ACTIVITY', activityType: null },
      { dateLocal: '2026-10-19' }, // Monday, first day of the week
      { dateLocal: '2026-10-25' }, // Sunday, last day of the week
      { weekStartLocal: '2026-12-28', dateLocal: '2027-01-03' }, // a week across the new year
    ]) {
      await assertSucceeds(createPost(as(BOB), GROUP_ID, uuid(), BOB, overrides));
    }
    // Optional fields may be absent
    const db = as(BOB);
    const minimal = post(BOB);
    delete minimal.authorPhotoUrl;
    delete minimal.durationSeconds;
    await assertSucceeds(setDoc(doc(db, postPath(uuid())), minimal));
  });

  it('denies a non-member, a guest, an anonymous account and posting as someone else', async () => {
    await assertFails(createPost(as(STRANGER), GROUP_ID, POST_ID, STRANGER));
    await assertFails(createPost(anonymous(), GROUP_ID, POST_ID, BOB));
    await assertFails(createPost(anonymousProvider(BOB), GROUP_ID, POST_ID, BOB));
    await assertFails(createPost(as(BOB), GROUP_ID, POST_ID, OWNER));
    await assertFails(createPost(as(BOB), OTHER_GROUP_ID, POST_ID, BOB));
  });

  it('denies invalid fields', async () => {
    for (const overrides of [
      { source: 'RUN' },
      { source: null },
      { title: 'x'.repeat(501) },
      { title: null },
      { durationSeconds: -1 },
      { durationSeconds: 2147483648 },
      { durationSeconds: 60.5 },
      { durationSeconds: '1800' },
      { authorName: '' },
      { authorName: 'x'.repeat(101) },
      { authorName: null },
      { authorPhotoUrl: 'http://example.com/a.png' },
      { authorPhotoUrl: `https://${'a'.repeat(2041)}` },
      { activityType: 'RUNNING' }, // only ACTIVITY posts carry a type
      { source: 'ACTIVITY', activityType: 'PARKOUR' },
      { schemaVersion: 2 },
      { reactions: null },
      { reactions: [] },
    ]) {
      await assertFails(createPost(as(BOB), GROUP_ID, uuid(), BOB, overrides));
    }
  });

  it('denies bad dates: format, impossible, not a Monday, outside the week', async () => {
    for (const overrides of [
      { dateLocal: '21/10/2026' },
      { dateLocal: '2026-13-01' },
      { dateLocal: '2026-10-21T10:00' },
      { weekStartLocal: '2026-10-20' }, // a Tuesday
      { weekStartLocal: '2026-10-25' }, // a Sunday
      { weekStartLocal: '2026-02-30' },
      { weekStartLocal: '19-10-2026' },
      { dateLocal: '2026-10-18' }, // the Sunday before
      { dateLocal: '2026-10-26' }, // the next Monday
    ]) {
      await assertFails(createPost(as(BOB), GROUP_ID, uuid(), BOB, overrides));
    }
  });

  it('denies a client time, reactions to start with, private or extra fields, a bad id', async () => {
    for (const overrides of [
      { createdAt: Timestamp.fromMillis(Date.now()) },
      { createdAt: Date.now() },
      { reactions: { [OWNER]: 'fire' } },
      { reactions: { [BOB]: 'strong' } },
      { expiresAt: Timestamp.fromMillis(Date.now() + POST_LIFETIME_MILLIS) },
      { note: 'Felt great' },
      { intensity: 'HARD' },
      { refId: 'gym:64' },
      { photoPath: 'training_photos/a.jpg' },
      { updatedAt: serverTimestamp() },
    ]) {
      await assertFails(createPost(as(BOB), GROUP_ID, uuid(), BOB, overrides));
    }
    const withoutTitle = post(BOB);
    delete withoutTitle.title;
    await assertFails(setDoc(doc(as(BOB), postPath(uuid())), withoutTitle));

    for (const badId of ['not-a-uuid', POST_ID.toUpperCase(), `${POST_ID}0`]) {
      await assertFails(createPost(as(BOB), GROUP_ID, badId, BOB));
    }
  });

  it('denies a thumbnail of 65,537 bytes or of the wrong type', async () => {
    await assertFails(createPost(as(BOB), GROUP_ID, uuid(), BOB, { thumb: thumbBytes(65537) }));
    await assertFails(createPost(as(BOB), GROUP_ID, uuid(), BOB, { thumb: 'iVBORw0KGgo=' }));
    await assertFails(createPost(as(BOB), GROUP_ID, uuid(), BOB, { thumb: [255, 216] }));
  });

  it('denies posting the same entry twice (the second write is an update)', async () => {
    await assertSucceeds(createPost(as(BOB), GROUP_ID, POST_ID, BOB));
    await assertFails(createPost(as(BOB), GROUP_ID, POST_ID, BOB, { title: 'Again' }));
  });

  it('a member posts to each of their groups with one write each', async () => {
    await assertSucceeds(createGroup(as(CARA), CARA, OTHER_GROUP_ID, NEW_INVITE_CODE));
    await assertSucceeds(joinGroup(as(BOB), BOB, OTHER_GROUP_ID, NEW_INVITE_CODE));
    await assertSucceeds(createPost(as(BOB), GROUP_ID, POST_ID, BOB));
    await assertSucceeds(createPost(as(BOB), OTHER_GROUP_ID, POST_ID, BOB));
    expect(await read(postPath(POST_ID, OTHER_GROUP_ID))).not.toBeNull();
  });
});

describe('read the feed', () => {
  beforeEach(async () => {
    await groupWith(BOB);
    await assertSucceeds(createPost(as(BOB), GROUP_ID, POST_ID, BOB));
  });

  it("members read the week's feed and single posts", async () => {
    const feed = await assertSucceeds(weekFeed(as(OWNER), GROUP_ID, '2026-10-19'));
    expect(feed.docs.map((d) => d.id)).toEqual([POST_ID]);
    await assertSucceeds(getDoc(doc(as(BOB), postPath())));
  });

  it('denies non-members, guests, anonymous accounts and removed members', async () => {
    await assertFails(getDoc(doc(as(STRANGER), postPath())));
    await assertFails(getDocs(collection(as(STRANGER), `groups/${GROUP_ID}/posts`)));
    await assertFails(getDoc(doc(anonymous(), postPath())));
    await assertFails(getDoc(doc(anonymousProvider(BOB), postPath())));

    await assertSucceeds(removeMember(as(OWNER), GROUP_ID, BOB));
    await assertFails(getDoc(doc(as(BOB), postPath())));
    await assertFails(weekFeed(as(BOB), GROUP_ID, '2026-10-19'));
  });

  it('denies reading posts of a group that no longer exists', async () => {
    // A member doc left behind by a Delete group must not open the posts that were missed
    await seed(postPath(), post(BOB));
    await assertSucceeds(deleteGroup(as(OWNER), OWNER, GROUP_ID, INVITE_CODE, [OWNER]));
    await assertFails(getDoc(doc(as(BOB), postPath())));
  });
});

describe('reactions', () => {
  beforeEach(async () => {
    await groupWith(BOB, CARA);
    await assertSucceeds(createPost(as(BOB), GROUP_ID, POST_ID, BOB));
  });

  it('a member adds, changes and removes their own reaction', async () => {
    await assertSucceeds(react(as(CARA), GROUP_ID, POST_ID, CARA, 'strong'));
    await assertSucceeds(react(as(OWNER), GROUP_ID, POST_ID, OWNER, 'fire'));
    await assertSucceeds(react(as(BOB), GROUP_ID, POST_ID, BOB, 'raised')); // the author too
    expect((await read(postPath())).reactions).toEqual({ [CARA]: 'strong', [OWNER]: 'fire', [BOB]: 'raised' });

    await assertSucceeds(react(as(CARA), GROUP_ID, POST_ID, CARA, 'clap'));
    await assertSucceeds(react(as(OWNER), GROUP_ID, POST_ID, OWNER, null));
    expect((await read(postPath())).reactions).toEqual({ [CARA]: 'clap', [BOB]: 'raised' });
  });

  it("denies touching another member's reaction", async () => {
    await assertSucceeds(react(as(CARA), GROUP_ID, POST_ID, CARA, 'strong'));
    await assertFails(react(as(OWNER), GROUP_ID, POST_ID, CARA, 'fire'));
    await assertFails(react(as(OWNER), GROUP_ID, POST_ID, CARA, null));
    await assertFails(react(as(OWNER), GROUP_ID, POST_ID, DANI, 'fire'));
    // Replacing the whole map, even keeping Cara's value, while adding mine and someone else's
    await assertFails(updateDoc(doc(as(OWNER), postPath()), { reactions: { [CARA]: 'strong', [OWNER]: 'fire', [DANI]: 'clap' } }));
    await assertFails(updateDoc(doc(as(OWNER), postPath()), { reactions: {} }));
  });

  it('denies an invalid code, removing the map, or changing other fields while reacting', async () => {
    for (const code of ['💪', 'heart', 'STRONG', '', 1, true]) {
      await assertFails(react(as(CARA), GROUP_ID, POST_ID, CARA, code));
    }
    await assertFails(updateDoc(doc(as(CARA), postPath()), { reactions: null }));
    await assertFails(updateDoc(doc(as(CARA), postPath()), { [`reactions.${CARA}`]: 'fire', title: 'Mine now' }));
    await assertFails(updateDoc(doc(as(BOB), postPath()), { [`reactions.${BOB}`]: 'fire', thumb: thumbBytes(10) }));
  });

  it('denies a non-member, a removed member and a guest reacting', async () => {
    await assertFails(react(as(STRANGER), GROUP_ID, POST_ID, STRANGER, 'fire'));
    await assertFails(react(anonymous(), GROUP_ID, POST_ID, CARA, 'fire'));
    await assertSucceeds(removeMember(as(OWNER), GROUP_ID, CARA));
    await assertFails(react(as(CARA), GROUP_ID, POST_ID, CARA, 'fire'));
  });
});

describe('the author edits the thumbnail only', () => {
  beforeEach(async () => {
    await groupWith(BOB, CARA);
    await assertSucceeds(createPost(as(BOB), GROUP_ID, POST_ID, BOB));
  });

  it('sets, replaces and removes the thumbnail', async () => {
    await assertSucceeds(setThumb(as(BOB), GROUP_ID, POST_ID, thumbBytes(1000)));
    await assertSucceeds(setThumb(as(BOB), GROUP_ID, POST_ID, thumbBytes(65536)));
    await assertSucceeds(setThumb(as(BOB), GROUP_ID, POST_ID, null));
    expect((await read(postPath())).thumb).toBeUndefined();
    await assertSucceeds(updateDoc(doc(as(BOB), postPath()), { thumb: null }));
  });

  it('denies other fields, a too big or wrong thumbnail, and anyone else', async () => {
    for (const fields of [
      { title: 'Leg Day' },
      { durationSeconds: 60 },
      { createdAt: serverTimestamp() },
      { authorUid: CARA },
      { weekStartLocal: '2026-10-26', dateLocal: '2026-10-27' },
      { thumb: thumbBytes(65537) },
      { thumb: 'iVBORw0KGgo=' },
      { thumb: thumbBytes(10), title: 'Leg Day' },
    ]) {
      await assertFails(updateDoc(doc(as(BOB), postPath()), fields));
    }
    await assertFails(setThumb(as(CARA), GROUP_ID, POST_ID, thumbBytes(10)));
    await assertSucceeds(setThumb(as(BOB), GROUP_ID, POST_ID, thumbBytes(10)));
    await assertFails(setThumb(as(OWNER), GROUP_ID, POST_ID, null));
    await assertFails(setThumb(as(OWNER), GROUP_ID, POST_ID, thumbBytes(20)));
  });

  it('denies a removed author changing the thumbnail', async () => {
    await assertSucceeds(removeMember(as(OWNER), GROUP_ID, BOB));
    await assertFails(setThumb(as(BOB), GROUP_ID, POST_ID, thumbBytes(10)));
  });
});

describe('titleKey: the workout name each reader resolves in their own language', () => {
  beforeEach(async () => {
    await groupWith(BOB, CARA);
  });

  const KEY_80 = `program/${'A'.repeat(72)}`;

  it('accepts a key of each kind on its own source, up to 80 characters, or none', async () => {
    for (const [source, titleKey] of [
      ['GYM', 'gym/64'],
      ['GYM', 'gym/999999999'],
      ['DAILY', 'daily/1'],
      ['DAILY', 'daily/30'],
      ['PROGRAM', 'program/UPPER_A'],
      ['PROGRAM', 'program/TREADMILL'],
      ['PROGRAM', 'program/FULL_BODY_C'],
      ['PROGRAM', KEY_80],
    ]) {
      await assertSucceeds(createPost(as(BOB), GROUP_ID, uuid(), BOB, { source, titleKey }));
    }
    expect(KEY_80.length).toBe(80);
    // Optional: older apps send none, and an activity is named by its activityType
    await assertSucceeds(createPost(as(BOB), GROUP_ID, uuid(), BOB));
    await assertSucceeds(createPost(as(BOB), GROUP_ID, uuid(), BOB, { titleKey: null }));
    await assertSucceeds(createPost(as(BOB), GROUP_ID, uuid(), BOB, { source: 'ACTIVITY', title: '', activityType: 'RUNNING' }));
    const id = uuid();
    await assertSucceeds(createPost(as(BOB), GROUP_ID, id, BOB, { titleKey: 'gym/64' }));
    expect((await read(postPath(id))).titleKey).toBe('gym/64');
  });

  it('denies a bad format', async () => {
    for (const titleKey of [
      '', 'gym/', 'gym/0', 'gym/064', 'gym/x', 'gym/6a', 'gym/-1', 'gym/1234567890', 'GYM/64', 'gym:64',
      ' gym/64', 'gym/64 ', 'gym/64/extra', 'gym/64\n', 'routine/64', 64, ['gym/64'], { kind: 'gym', id: 64 },
    ]) {
      await assertFails(createPost(as(BOB), GROUP_ID, uuid(), BOB, { source: 'GYM', titleKey }));
    }
    for (const titleKey of ['daily/', 'daily/0', 'daily/01', 'daily/day1', 'daily/1234567890', 'daily/1 ']) {
      await assertFails(createPost(as(BOB), GROUP_ID, uuid(), BOB, { source: 'DAILY', titleKey }));
    }
    for (const titleKey of [
      'program/', 'program/upper_a', 'program/Upper_A', 'program/UPPER-A', 'program/1UPPER', 'program/_UPPER',
      'program/UPPER A', 'program/UPPER_A/2', 'program/ÜBER', 'program:UPPER_A',
    ]) {
      await assertFails(createPost(as(BOB), GROUP_ID, uuid(), BOB, { source: 'PROGRAM', titleKey }));
    }
  });

  it("denies a key of another source's kind, and any key on an activity", async () => {
    for (const [source, titleKey] of [
      ['DAILY', 'gym/64'],
      ['PROGRAM', 'gym/64'],
      ['GYM', 'daily/3'],
      ['PROGRAM', 'daily/3'],
      ['GYM', 'program/UPPER_A'],
      ['DAILY', 'program/UPPER_A'],
      ['ACTIVITY', 'gym/64'],
      ['ACTIVITY', 'daily/3'],
      ['ACTIVITY', 'program/UPPER_A'],
    ]) {
      await assertFails(createPost(as(BOB), GROUP_ID, uuid(), BOB, { source, titleKey }));
    }
  });

  it('denies a key over 80 characters', async () => {
    const tooLong = `program/${'A'.repeat(73)}`;
    expect(tooLong.length).toBe(81);
    await assertFails(createPost(as(BOB), GROUP_ID, uuid(), BOB, { source: 'PROGRAM', titleKey: tooLong }));
    await assertFails(createPost(as(BOB), GROUP_ID, uuid(), BOB, { source: 'PROGRAM', titleKey: `program/${'A'.repeat(500)}` }));
  });

  it('never changes after creation: not by the author, a member or the owner', async () => {
    const withKey = uuid();
    const withoutKey = uuid();
    await assertSucceeds(createPost(as(BOB), GROUP_ID, withKey, BOB, { titleKey: 'gym/64' }));
    await assertSucceeds(createPost(as(BOB), GROUP_ID, withoutKey, BOB));
    const ref = (id) => doc(as(BOB), postPath(id));
    for (const fields of [
      { titleKey: 'gym/65' },
      { titleKey: null },
      { titleKey: 'gym/65', thumb: thumbBytes(10) },
      { titleKey: 'gym/65', [`reactions.${BOB}`]: 'fire' },
    ]) {
      await assertFails(updateDoc(ref(withKey), fields));
    }
    await assertFails(updateDoc(ref(withKey), { titleKey: deleteField() }));
    await assertFails(updateDoc(ref(withoutKey), { titleKey: 'gym/64' }));
    await assertFails(updateDoc(ref(withoutKey), { titleKey: 'gym/64', thumb: thumbBytes(10) }));
    await assertFails(updateDoc(doc(as(CARA), postPath(withKey)), { titleKey: 'gym/65', [`reactions.${CARA}`]: 'clap' }));
    await assertFails(updateDoc(doc(as(OWNER), postPath(withKey)), { titleKey: 'gym/65' }));
    // The allowed updates keep it
    await assertSucceeds(setThumb(as(BOB), GROUP_ID, withKey, thumbBytes(10)));
    await assertSucceeds(react(as(CARA), GROUP_ID, withKey, CARA, 'strong'));
    expect((await read(postPath(withKey))).titleKey).toBe('gym/64');
  });
});

describe('delete a post', () => {
  beforeEach(async () => {
    await groupWith(BOB, CARA);
    await assertSucceeds(createPost(as(BOB), GROUP_ID, POST_ID, BOB));
  });

  it('the author deletes it', async () => {
    await assertSucceeds(deletePost(as(BOB), GROUP_ID, POST_ID));
    expect(await read(postPath())).toBeNull();
  });

  it('the group owner deletes any post', async () => {
    await assertSucceeds(deletePost(as(OWNER), GROUP_ID, POST_ID));
    expect(await read(postPath())).toBeNull();
  });

  it('denies another member before expiry, a non-member and a guest', async () => {
    await assertFails(deletePost(as(CARA), GROUP_ID, POST_ID));
    await assertFails(deletePost(as(STRANGER), GROUP_ID, POST_ID));
    await assertFails(deletePost(anonymous(), GROUP_ID, POST_ID));
    expect(await read(postPath())).not.toBeNull();
  });

  it('any member deletes an expired post; not one created less than 8 days ago', async () => {
    const expired = uuid();
    const almost = uuid();
    await seedPostCreatedAgo(expired, BOB, POST_LIFETIME_MILLIS + 60_000);
    await seedPostCreatedAgo(almost, BOB, POST_LIFETIME_MILLIS - 60 * 60_000);

    await assertFails(deletePost(as(STRANGER), GROUP_ID, expired));
    await assertSucceeds(deletePost(as(CARA), GROUP_ID, expired));
    await assertFails(deletePost(as(CARA), GROUP_ID, almost));
  });

  it("the cleanup deletes expired posts and skips the ones the server's clock keeps", async () => {
    const expiredIds = [uuid(), uuid(), uuid()];
    for (const id of expiredIds) await seedPostCreatedAgo(id, BOB, 9 * DAY);
    const almost = uuid();
    await seedPostCreatedAgo(almost, OWNER, POST_LIFETIME_MILLIS - 60 * 60_000);

    // A device clock 2 hours ahead also selects [almost]: the server refuses it, the others go
    const deleted = await cleanUpExpired(as(CARA), GROUP_ID, Date.now() + 2 * 60 * 60_000);
    expect(deleted).toBe(3);
    expect(await read(postPath(almost))).not.toBeNull();
    for (const id of expiredIds) expect(await read(postPath(id))).toBeNull();
    expect(await cleanUpExpired(as(CARA), GROUP_ID, Date.now(), 2)).toBe(0);
  });

  it('a member deletes a post that does not exist (no-op); a non-member cannot', async () => {
    await assertSucceeds(deletePost(as(CARA), GROUP_ID, uuid()));
    await assertFails(deletePost(as(STRANGER), GROUP_ID, uuid()));
  });

  it('a deleted entry deletes its post in every group, with one delete per group', async () => {
    await assertSucceeds(createGroup(as(BOB), BOB, OTHER_GROUP_ID, NEW_INVITE_CODE));
    await assertSucceeds(createPost(as(BOB), OTHER_GROUP_ID, POST_ID, BOB));
    const third = groupId(3);
    await assertSucceeds(createGroup(as(DANI), DANI, third, inviteCode(3)));
    await assertSucceeds(joinGroup(as(BOB), BOB, third, inviteCode(3))); // joined, never posted there

    for (const g of [GROUP_ID, OTHER_GROUP_ID, third]) await assertSucceeds(deletePost(as(BOB), g, POST_ID));
    expect(await read(postPath(POST_ID, GROUP_ID))).toBeNull();
    expect(await read(postPath(POST_ID, OTHER_GROUP_ID))).toBeNull();
  });
});

describe('membership changes with posts', () => {
  it('leave: the member deletes their own posts, then leaves', async () => {
    await groupWith(BOB, CARA);
    await assertSucceeds(createPost(as(BOB), GROUP_ID, POST_ID, BOB));
    await assertSucceeds(createPost(as(BOB), GROUP_ID, uuid(), BOB));
    await assertSucceeds(createPost(as(CARA), GROUP_ID, uuid(), CARA));

    expect(await deletePostsBy(as(BOB), GROUP_ID, BOB)).toBe(2);
    await assertSucceeds(leaveGroup(as(BOB), BOB, GROUP_ID));

    const left = await assertSucceeds(getDocs(collection(as(CARA), `groups/${GROUP_ID}/posts`)));
    expect(left.docs.map((d) => d.data().authorUid)).toEqual([CARA]);
    await assertFails(createPost(as(BOB), GROUP_ID, uuid(), BOB));
  });

  it("a member can't delete someone else's posts on the way out", async () => {
    await groupWith(BOB, CARA);
    await assertSucceeds(createPost(as(CARA), GROUP_ID, uuid(), CARA));
    await expect(deletePostsBy(as(BOB), GROUP_ID, CARA)).rejects.toThrow();
  });

  it('an author who already left still deletes their post by id', async () => {
    await groupWith(BOB);
    await assertSucceeds(createPost(as(BOB), GROUP_ID, POST_ID, BOB));
    await assertSucceeds(leaveGroup(as(BOB), BOB, GROUP_ID));
    await assertSucceeds(deletePost(as(BOB), GROUP_ID, POST_ID));
  });

  it("remove member: the owner removes them, then deletes their posts; they can't post again", async () => {
    await groupWith(BOB, CARA);
    await assertSucceeds(createPost(as(BOB), GROUP_ID, POST_ID, BOB));
    await assertSucceeds(createPost(as(BOB), GROUP_ID, uuid(), BOB, { thumb: thumbBytes(60000) }));
    await assertSucceeds(createPost(as(CARA), GROUP_ID, uuid(), CARA));

    await assertSucceeds(removeMember(as(OWNER), GROUP_ID, BOB));
    expect(await deletePostsBy(as(OWNER), GROUP_ID, BOB)).toBe(2);
    await assertFails(createPost(as(BOB), GROUP_ID, uuid(), BOB));
    const left = await assertSucceeds(getDocs(collection(as(CARA), `groups/${GROUP_ID}/posts`)));
    expect(left.size).toBe(1);
  });

  it('delete group: the owner deletes every post, then the members, then the group', async () => {
    await groupWith(BOB, CARA);
    for (const uid of [OWNER, BOB, CARA, BOB]) await assertSucceeds(createPost(as(uid), GROUP_ID, uuid(), uid));

    expect(await deleteAllPosts(as(OWNER), GROUP_ID)).toBe(4);
    await assertSucceeds(removeMember(as(OWNER), GROUP_ID, BOB));
    await assertSucceeds(removeMember(as(OWNER), GROUP_ID, CARA));
    await assertSucceeds(deleteGroup(as(OWNER), OWNER, GROUP_ID, INVITE_CODE, [OWNER]));
    expect(await read(`groups/${GROUP_ID}`)).toBeNull();
  });

  it('a post the delete missed stays unreadable; its author deletes it', async () => {
    await groupWith(BOB);
    await assertSucceeds(createPost(as(BOB), GROUP_ID, POST_ID, BOB));
    await assertSucceeds(removeMember(as(OWNER), GROUP_ID, BOB));
    await assertSucceeds(deleteGroup(as(OWNER), OWNER, GROUP_ID, INVITE_CODE, [OWNER]));

    await assertFails(getDoc(doc(as(OWNER), postPath())));
    await assertFails(deletePost(as(OWNER), GROUP_ID, POST_ID)); // no owner once the group is gone
    await assertSucceeds(deletePost(as(BOB), GROUP_ID, POST_ID));
  });

  it('delete account: deletes my posts in every group before leaving them', async () => {
    const G_MEMBER = groupId(1); // DANI is a member
    const G_SHARED = groupId(2); // DANI owns it with BOB (oldest) and CARA
    const G_SOLO = groupId(3); // DANI owns it alone
    await assertSucceeds(createGroup(as(OWNER), OWNER, G_MEMBER, inviteCode(1)));
    await assertSucceeds(joinGroup(as(DANI), DANI, G_MEMBER, inviteCode(1)));
    await assertSucceeds(createGroup(as(DANI), DANI, G_SHARED, inviteCode(2)));
    await assertSucceeds(joinGroup(as(BOB), BOB, G_SHARED, inviteCode(2)));
    await assertSucceeds(joinGroup(as(CARA), CARA, G_SHARED, inviteCode(2)));
    await assertSucceeds(createGroup(as(DANI), DANI, G_SOLO, inviteCode(3)));
    // DANI posts one workout to all three groups; BOB posts to the shared one
    for (const g of [G_MEMBER, G_SHARED, G_SOLO]) await assertSucceeds(createPost(as(DANI), g, POST_ID, DANI));
    const bobPost = uuid();
    await assertSucceeds(createPost(as(BOB), G_SHARED, bobPost, BOB));

    const db = as(DANI);
    expect(await deletePostsBy(db, G_MEMBER, DANI)).toBe(1);
    await assertSucceeds(leaveGroup(db, DANI, G_MEMBER));
    expect(await deletePostsBy(db, G_SHARED, DANI)).toBe(1);
    await assertSucceeds(leaveGroup(db, DANI, G_SHARED, { newOwnerUid: BOB }));
    expect(await deleteAllPosts(db, G_SOLO)).toBe(1);
    await assertSucceeds(deleteGroup(db, DANI, G_SOLO, inviteCode(3), [DANI]));
    await assertSucceeds(deleteGroupCounter(db, DANI));

    for (const g of [G_MEMBER, G_SHARED, G_SOLO]) expect(await read(postPath(POST_ID, g))).toBeNull();
    expect(await read(postPath(bobPost, G_SHARED))).not.toBeNull();
    // The new owner of the shared group can moderate it
    await assertSucceeds(deletePost(as(BOB), G_SHARED, bobPost));
  });
});

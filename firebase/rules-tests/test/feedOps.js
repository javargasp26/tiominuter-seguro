// The feed writes and queries of docs/firestore-model.md ("Feed"), as the app (Phase 4b) will send
// them. Each function runs as [db]'s user.
import {
  Timestamp,
  collection,
  deleteDoc,
  deleteField,
  doc,
  getDocs,
  limit,
  orderBy,
  query,
  setDoc,
  updateDoc,
  where,
} from 'firebase/firestore';
import { post } from './fixtures.js';

export const POST_LIFETIME_MILLIS = 8 * 24 * 60 * 60 * 1000;

const postRef = (db, groupId, postId) => doc(db, `groups/${groupId}/posts/${postId}`);

/** Post one workout to one group (the app sends one write per group, never one batch for all). */
export function createPost(db, groupId, postId, authorUid, overrides = {}) {
  return setDoc(postRef(db, groupId, postId), post(authorUid, overrides));
}

/** My reaction: a code sets or changes it, null removes it (a field-path update, no read first). */
export function react(db, groupId, postId, uid, code) {
  return updateDoc(postRef(db, groupId, postId), { [`reactions.${uid}`]: code ?? deleteField() });
}

/** The author's thumbnail: bytes set or replace it, null removes the field. */
export function setThumb(db, groupId, postId, bytes) {
  return updateDoc(postRef(db, groupId, postId), { thumb: bytes ?? deleteField() });
}

export function deletePost(db, groupId, postId) {
  return deleteDoc(postRef(db, groupId, postId));
}

/** This week's feed, newest first (the composite index of firestore.indexes.json). */
export function weekFeed(db, groupId, weekStartLocal, pageSize = 50) {
  return getDocs(query(
    collection(db, `groups/${groupId}/posts`),
    where('weekStartLocal', '==', weekStartLocal),
    orderBy('createdAt', 'desc'),
    limit(pageSize),
  ));
}

/**
 * Deletes every post of [authorUid] in the group, one delete each: my own before leaving, or a
 * removed member's (the owner, after the removal). Returns how many were deleted.
 */
export async function deletePostsBy(db, groupId, authorUid) {
  const posts = await getDocs(query(collection(db, `groups/${groupId}/posts`), where('authorUid', '==', authorUid)));
  for (const d of posts.docs) await deleteDoc(d.ref);
  return posts.size;
}

/** Delete group, first step (owner): every post, while the group still exists. */
export async function deleteAllPosts(db, groupId) {
  const posts = await getDocs(collection(db, `groups/${groupId}/posts`));
  for (const d of posts.docs) await deleteDoc(d.ref);
  return posts.size;
}

/**
 * The Spark cleanup (any member, when a feed opens): up to [max] posts created more than 8 days
 * before [nowMillis] (the device clock, so the server may still refuse some: each is deleted on
 * its own and a refusal is skipped). Returns how many were deleted.
 */
export async function cleanUpExpired(db, groupId, nowMillis, max = 20) {
  const expired = await getDocs(query(
    collection(db, `groups/${groupId}/posts`),
    where('createdAt', '<', Timestamp.fromMillis(nowMillis - POST_LIFETIME_MILLIS)),
    orderBy('createdAt'),
    limit(max),
  ));
  let deleted = 0;
  for (const d of expired.docs) {
    try {
      await deleteDoc(d.ref);
      deleted++;
    } catch {
      // not expired yet on the server's clock: the next cleanup gets it
    }
  }
  return deleted;
}

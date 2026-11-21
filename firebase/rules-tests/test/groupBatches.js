// The batched writes of every group operation, as docs/firestore-model.md ("Groups") describes
// them and as the app (Phase 3b) will send them. Each function commits one batch as [db]'s user.
import { deleteDoc, doc, increment, serverTimestamp, writeBatch } from 'firebase/firestore';
import { group, invite, member, membership } from './fixtures.js';

/** users/{uid}/meta/groups: groupCount ± 1 naming the group (a merge, so no read is needed). */
function counterStep(batch, db, uid, groupId, step) {
  batch.set(
    doc(db, `users/${uid}/meta/groups`),
    { groupCount: increment(step), lastGroupId: groupId, updatedAt: serverTimestamp(), schemaVersion: 1 },
    { merge: true },
  );
}

/** Create: group, the creator's member doc, the invite code, the index entry, counter + 1. */
export function createGroup(db, uid, groupId, code, { name = 'Morning crew', summary = {} } = {}) {
  const batch = writeBatch(db);
  batch.set(doc(db, `groups/${groupId}`), group(uid, { name, inviteCode: code }));
  batch.set(doc(db, `groups/${groupId}/members/${uid}`), member({ inviteCode: code, ...summary }));
  batch.set(doc(db, `inviteCodes/${code}`), invite(groupId, { groupName: name }));
  batch.set(doc(db, `users/${uid}/groups/${groupId}`), membership(groupId));
  counterStep(batch, db, uid, groupId, 1);
  return batch.commit();
}

/**
 * Join with a code looked up in inviteCodes/{code}: memberCount + 1 (an increment, since a
 * non-member can't read the group), the member doc carrying the code, the index, counter + 1.
 */
export function joinGroup(db, uid, groupId, code, { summary = {}, groupUpdate = {} } = {}) {
  const batch = writeBatch(db);
  batch.update(doc(db, `groups/${groupId}`), {
    memberCount: increment(1),
    memberChangeUid: uid,
    updatedAt: serverTimestamp(),
    ...groupUpdate,
  });
  batch.set(doc(db, `groups/${groupId}/members/${uid}`), member({ inviteCode: code, ...summary }));
  batch.set(doc(db, `users/${uid}/groups/${groupId}`), membership(groupId));
  counterStep(batch, db, uid, groupId, 1);
  return batch.commit();
}

/**
 * Leave: memberCount - 1, own member doc and index entry deleted, counter - 1. An owner passes
 * the group to [newOwnerUid] in the same write.
 */
export function leaveGroup(db, uid, groupId, { newOwnerUid = null, decrement = true, deleteIndex = true } = {}) {
  const batch = writeBatch(db);
  batch.update(doc(db, `groups/${groupId}`), {
    ...(decrement ? { memberCount: increment(-1) } : {}),
    memberChangeUid: uid,
    updatedAt: serverTimestamp(),
    ...(newOwnerUid ? { ownerUid: newOwnerUid } : {}),
  });
  batch.delete(doc(db, `groups/${groupId}/members/${uid}`));
  if (deleteIndex) {
    batch.delete(doc(db, `users/${uid}/groups/${groupId}`));
    counterStep(batch, db, uid, groupId, -1);
  }
  return batch.commit();
}

/** Remove member (owner): memberCount - 1 naming them, their member doc deleted. */
export function removeMember(db, groupId, uid, { decrement = true } = {}) {
  const batch = writeBatch(db);
  batch.update(doc(db, `groups/${groupId}`), {
    ...(decrement ? { memberCount: increment(-1) } : {}),
    memberChangeUid: uid,
    updatedAt: serverTimestamp(),
  });
  batch.delete(doc(db, `groups/${groupId}/members/${uid}`));
  return batch.commit();
}

/** Transfer (owner): a single update of ownerUid. */
export function transferGroup(db, groupId, newOwnerUid) {
  const batch = writeBatch(db);
  batch.update(doc(db, `groups/${groupId}`), { ownerUid: newOwnerUid, updatedAt: serverTimestamp() });
  return batch.commit();
}

/** Rename (owner): the group and its invite code doc. */
export function renameGroup(db, groupId, code, name, { updateInvite = true } = {}) {
  const batch = writeBatch(db);
  batch.update(doc(db, `groups/${groupId}`), { name, updatedAt: serverTimestamp() });
  if (updateInvite) batch.update(doc(db, `inviteCodes/${code}`), { groupName: name });
  return batch.commit();
}

/** Regenerate the code (owner): old invite doc deleted, new one created, group.inviteCode. */
export function regenerateCode(db, groupId, oldCode, newCode, { name = 'Morning crew', deleteOld = true } = {}) {
  const batch = writeBatch(db);
  batch.update(doc(db, `groups/${groupId}`), { inviteCode: newCode, updatedAt: serverTimestamp() });
  if (deleteOld) batch.delete(doc(db, `inviteCodes/${oldCode}`));
  batch.set(doc(db, `inviteCodes/${newCode}`), invite(groupId, { groupName: name }));
  return batch.commit();
}

/**
 * Delete (owner; also an owner leaving alone): every member doc, the group, the invite code,
 * the owner's index entry and counter - 1.
 */
export function deleteGroup(db, ownerUid, groupId, code, memberUids, { deleteInvite = true } = {}) {
  const batch = writeBatch(db);
  for (const uid of memberUids) batch.delete(doc(db, `groups/${groupId}/members/${uid}`));
  batch.delete(doc(db, `groups/${groupId}`));
  if (deleteInvite) batch.delete(doc(db, `inviteCodes/${code}`));
  batch.delete(doc(db, `users/${ownerUid}/groups/${groupId}`));
  counterStep(batch, db, ownerUid, groupId, -1);
  return batch.commit();
}

/** Reconcile a stale index entry (group gone, or no longer a member): entry deleted, counter - 1. */
export function reconcileMembership(db, uid, groupId) {
  const batch = writeBatch(db);
  batch.delete(doc(db, `users/${uid}/groups/${groupId}`));
  counterStep(batch, db, uid, groupId, -1);
  return batch.commit();
}

/** Delete account, last step: the counter, once the person is in no group. */
export function deleteGroupCounter(db, uid) {
  return deleteDoc(doc(db, `users/${uid}/meta/groups`));
}

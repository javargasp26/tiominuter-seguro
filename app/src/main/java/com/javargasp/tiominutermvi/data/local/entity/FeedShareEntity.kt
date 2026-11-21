package com.javargasp.tiominutermvi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One workout shared (or not) in my groups: the group feed's queue (database v10). Group id lists
 * are comma-separated (Firestore auto ids have no commas); [targetGroupIds] is null until the first
 * post resolves my groups. No FK to training_log: the row outlives its entry until its posts are
 * deleted.
 */
@Entity(tableName = "feed_share")
data class FeedShareEntity(
    @PrimaryKey val entryUuid: String,
    val ownerUid: String,
    val share: Boolean,
    val entryDeleted: Boolean,
    val targetGroupIds: String?,
    val postedGroupIds: String,
    val refusedGroupIds: String,
    val postedPhotoPath: String?,
    val createdAt: Long,
    val version: Long,
    val dirty: Boolean
)

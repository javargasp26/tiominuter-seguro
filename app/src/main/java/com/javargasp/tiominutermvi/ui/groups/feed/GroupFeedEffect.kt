package com.javargasp.tiominutermvi.ui.groups.feed

import com.javargasp.tiominutermvi.ui.base.MviEffect
import com.javargasp.tiominutermvi.ui.groups.common.GroupText

sealed interface GroupFeedEffect : MviEffect {
    data class ShowMessage(val text: GroupText) : GroupFeedEffect
}

package com.javargasp.tiominutermvi.data.content

/**
 * Resolves an exercise animation (a file name in `animations/`) to a Coil model. The only way
 * the UI finds an animation: never build an asset URL by hand.
 */
fun interface AnimationLocator {
    /** The bundled asset first, then a downloaded file; null when neither exists. */
    fun locate(animation: String): Any?

    companion object {
        const val ASSET_URL_PREFIX = "file:///android_asset/animations/"

        /** Bundled assets only (previews, where Koin isn't running). */
        val BundledOnly = AnimationLocator { ASSET_URL_PREFIX + it }
    }
}

/** [AnimationLocator] over the APK assets and this process's [ActiveContent]. */
class ContentAnimationLocator(
    private val bundled: BundledContent,
    private val store: ContentStore,
    private val active: ActiveContent
) : AnimationLocator {

    override fun locate(animation: String): Any? {
        if (animation in bundled.animationNames()) return AnimationLocator.ASSET_URL_PREFIX + animation
        val entry = active.entry(ContentRules.ANIMATIONS_DIR + animation) ?: return null
        return store.objectFile(entry.sha256).takeIf { it.isFile }
    }
}

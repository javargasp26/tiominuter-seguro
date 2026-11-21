package com.javargasp.tiominutermvi.testutil

import java.io.File

/**
 * The app's content (`content/v1` at the repository root): the files the APK bundles as
 * assets and Firebase Hosting publishes. Unit tests read it by relative path because Gradle
 * runs them from the app module directory.
 */
val CONTENT_DIR = File("../content/v1")

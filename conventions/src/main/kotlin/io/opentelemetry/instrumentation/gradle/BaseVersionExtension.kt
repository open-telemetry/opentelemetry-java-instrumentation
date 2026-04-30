/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.gradle

import org.gradle.api.Project

/**
 * Project extension that produces a version string suitable for use inside `JvmTestSuite`
 * dependency blocks (where `library()` / `testLibrary()` are unavailable).
 *
 * In `build.gradle.kts`:
 * ```
 * implementation("g:a:${baseVersion("4.0.0").orLatest()}")
 * implementation("g:a:${baseVersion("4.0.0").orLatest("5.+")}")
 * ```
 *
 * `orLatest()` resolves to `"latest.release"` when `-PtestLatestDeps=true`, otherwise to the
 * base version. `orLatest(constraint)` lets the caller cap the latest-deps resolution to a
 * specific range (e.g. `"5.+"`) instead of letting it float to whatever the newest release
 * happens to be.
 */
open class BaseVersionExtension(private val project: Project) {
  operator fun invoke(baseVersion: String): BaseVersion = BaseVersion(project, baseVersion)
}

class BaseVersion(private val project: Project, private val baseVersion: String) {
  fun orLatest(): String = orLatest("latest.release")

  fun orLatest(latestConstraint: String): String =
    if (project.findProperty("testLatestDeps") == "true") latestConstraint else baseVersion
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.gradle.muzzle

import org.eclipse.aether.version.Version
import java.util.Locale
import java.util.function.Predicate
import java.util.regex.Pattern

internal class AcceptableVersions(private val skipVersions: Collection<String>) :
  Predicate<Version?> {

  override fun test(version: Version?): Boolean {
    if (version == null) {
      return false
    }
    val versionString = version.toString().toLowerCase(Locale.ROOT)
    if (skipVersions.contains(versionString)) {
      return false
    }
    val draftVersion = (versionString.contains("rc")
      || versionString.contains(".cr")
      || versionString.contains("alpha")
      || versionString.contains("beta")
      || versionString.contains("-b")
      || versionString.contains(".m")
      || versionString.contains("-m")
      || versionString.contains("-dev")
      || versionString.contains("-ea")
      || versionString.contains("-atlassian-")
      || versionString.contains("public_draft")
      || versionString.contains("snapshot")
      || GIT_SHA_PATTERN.matcher(versionString).matches())
    return !draftVersion
  }

  companion object {
    private val GIT_SHA_PATTERN = Pattern.compile("^.*-[0-9a-f]{7,}$")
  }
}

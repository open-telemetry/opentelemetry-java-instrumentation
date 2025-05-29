/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.muzzle

import org.eclipse.aether.version.Version
import java.util.Locale
import java.util.function.Predicate

class AcceptableVersions(private val skipVersions: Collection<String>) :
  Predicate<Version?> {

  override fun test(version: Version?): Boolean {
    if (version == null) {
      return false
    }
    val versionString = version.toString().lowercase(Locale.ROOT)
    if (skipVersions.contains(versionString)) {
      return false
    }
    val draftVersion = versionString.contains("rc")
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
      || versionString.contains("test")
      || versionString.startsWith("0.0.0-")
      || GIT_SHA_PATTERN.matches(versionString)
      || DATETIME_PATTERN.matches(versionString)
    return !draftVersion
  }

  companion object {
    private val GIT_SHA_PATTERN = Regex("^.*-[0-9a-f]{7,}$")
    private val DATETIME_PATTERN = Regex("^\\d{4}-\\d{2}-\\d{2}t\\d{2}-\\d{2}-\\d{2}.*$")
  }
}

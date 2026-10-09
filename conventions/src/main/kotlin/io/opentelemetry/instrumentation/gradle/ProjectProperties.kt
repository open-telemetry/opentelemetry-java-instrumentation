/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.gradle

import org.gradle.api.Project

// Gradle properties and extra properties can be inherited, but the parent lookup must be explicit.
fun Project.findInheritedExtraProperty(name: String): Any? {
  var current: Project? = this
  while (current != null) {
    val extra = current.extensions.extraProperties
    if (extra.has(name)) {
      return extra.get(name)
    }
    current = current.parent
  }
  return null
}

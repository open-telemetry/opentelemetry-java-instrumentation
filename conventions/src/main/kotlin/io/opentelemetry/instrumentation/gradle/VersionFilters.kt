/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.gradle

object VersionFilters {
  private val GIT_SHA_PATTERN = Regex("^.*-[0-9a-f]{7,}$")
  private val DATETIME_PATTERN = Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}-\\d{2}-\\d{2}.*$")

  fun isUnstable(version: String): Boolean {
    return version.contains("-alpha", true)
      || version.contains("-beta", true)
      || version.contains("-rc", true)
      || version.contains(".rc", true)
      || version.contains("-m", true) // e.g. spring milestones are published to grails repo
      || version.contains(".m", true) // e.g. lettuce
      || version.contains(".alpha", true) // e.g. netty
      || version.contains(".beta", true) // e.g. hibernate
      || version.contains(".cr", true) // e.g. hibernate
      || version.endsWith("-nf-execution") // graphql
      || GIT_SHA_PATTERN.matches(version) // graphql
      || DATETIME_PATTERN.matches(version) // graphql
  }
}

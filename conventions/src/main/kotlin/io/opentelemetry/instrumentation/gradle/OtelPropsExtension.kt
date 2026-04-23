/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.gradle

import org.gradle.api.JavaVersion
import org.gradle.api.Project

open class OtelPropsExtension(
  private val project: Project
) {
  val testLatestDeps: Boolean
    get() = project.findProperty("testLatestDeps") == "true"

  val denyUnsafe: Boolean
    get() = project.findProperty("denyUnsafe") == "true"

  val collectMetadata: Boolean
    get() = project.findProperty("collectMetadata") == "true"

  val testJavaVersion: JavaVersion?
    get() = project.findProperty("testJavaVersion")?.toString()?.let(JavaVersion::toVersion)

  val testJavaVM: String?
    get() = project.findProperty("testJavaVM")?.toString()

  val maxTestRetries: Int?
    get() = project.findProperty("maxTestRetries")?.toString()?.toInt()

  val enableStrictContext: Boolean
    get() = project.findProperty("enableStrictContext")?.toString()?.toBoolean() ?: true
}

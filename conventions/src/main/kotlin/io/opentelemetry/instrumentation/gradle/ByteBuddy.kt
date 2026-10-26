/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.gradle

import org.gradle.api.Project
import org.gradle.api.attributes.Attribute
import org.gradle.api.tasks.SourceSet

enum class ByteBuddyVariant(val artifact: String) {
  STANDARD("byte-buddy"),
  EXTERNAL_ASM("byte-buddy-dep")
}

fun Project.useByteBuddy(sourceSet: SourceSet, variant: ByteBuddyVariant) {
  for (name in listOf(sourceSet.compileClasspathConfigurationName, sourceSet.runtimeClasspathConfigurationName)) {
    configurations.named(name) {
      attributes.attribute(Attribute.of("io.opentelemetry.byte-buddy-variant", String::class.java), variant.artifact)
    }
  }
}

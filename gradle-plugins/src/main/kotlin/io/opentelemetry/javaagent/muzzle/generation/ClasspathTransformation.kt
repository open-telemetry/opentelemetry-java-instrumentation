/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.muzzle.generation

import java.io.File
import net.bytebuddy.build.Plugin.Factory.UsingReflection.ArgumentResolver
import net.bytebuddy.build.gradle.Transformation
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input

/**
 * Special implementation of [Transformation] is required as classpath argument must be
 * exposed to Gradle via [Classpath] annotation, which cannot be done if it is returned by
 * [Transformation.getArguments].
 */
class ClasspathTransformation(
  @get:Classpath val classpath: Iterable<File>,
  @get:Input val pluginClassName: String
) : Transformation() {
  override fun makeArgumentResolvers(): List<ArgumentResolver> {
    return listOf(
      ArgumentResolver.ForIndex(0, classpath),
      ArgumentResolver.ForIndex(2, pluginClassName)
    )
  }
}

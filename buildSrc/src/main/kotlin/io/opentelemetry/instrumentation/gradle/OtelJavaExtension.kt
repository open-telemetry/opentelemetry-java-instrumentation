/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.gradle

import org.gradle.api.JavaVersion
import org.gradle.api.provider.Property

abstract class OtelJavaExtension {
  abstract val minJavaVersionSupported: Property<JavaVersion>

  abstract val maxJavaVersionForTests: Property<JavaVersion>

  init {
    minJavaVersionSupported.convention(JavaVersion.VERSION_1_8)
  }
}

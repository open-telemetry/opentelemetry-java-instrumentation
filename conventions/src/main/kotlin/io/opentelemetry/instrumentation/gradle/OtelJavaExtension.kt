/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.gradle

import org.gradle.api.JavaVersion
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

abstract class OtelJavaExtension {
  abstract val minJavaVersionSupported: Property<JavaVersion>
  abstract val maxJavaVersionSupported: Property<JavaVersion>

  abstract val maxJavaVersionForTests: Property<JavaVersion>

  // When false, skips OSGi bundle metadata generation for a module that has otel.osgi-conventions
  // applied (e.g. a library that can't be a clean bundle). Has no effect on modules that don't
  // apply otel.osgi-conventions.
  abstract val osgiEnabled: Property<Boolean>

  // Extra packages added to Import-Package as optional imports (resolution:=optional), typically
  // corresponding to compileOnly dependencies that are not present at runtime in an OSGi container.
  abstract val osgiOptionalPackages: ListProperty<String>

  init {
    minJavaVersionSupported.convention(JavaVersion.VERSION_1_8)
    osgiEnabled.convention(true)
    osgiOptionalPackages.convention(emptyList())
  }
}

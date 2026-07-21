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

  // Extra packages added to Import-Package as optional imports (resolution:=optional), typically
  // corresponding to compileOnly dependencies that are not present at runtime in an OSGi container.
  // Only consulted by modules that apply otel.osgi-conventions.
  abstract val osgiOptionalPackages: ListProperty<String>

  // Explicit Import-Package clauses (raw bnd syntax, e.g. an explicit version range or
  // resolution:=optional) inserted ahead of the wildcard imports. Use to widen or relax the version
  // ranges bnd infers from compile-time dependencies, or to make a specific package optional. Only
  // consulted by modules that apply otel.osgi-conventions.
  abstract val osgiImportPackages: ListProperty<String>

  init {
    minJavaVersionSupported.convention(JavaVersion.VERSION_1_8)
    osgiOptionalPackages.convention(emptyList())
    osgiImportPackages.convention(emptyList())
  }
}

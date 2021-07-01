/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
  id("otel.library-instrumentation")
}

dependencies {
  implementation(project(":instrumentation-api"))

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  implementation("org.slf4j:slf4j-api")

  testImplementation(project(":instrumentation:jdbc:testing"))
}

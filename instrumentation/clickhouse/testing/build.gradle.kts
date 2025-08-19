/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
  id("otel.javaagent-testing")
}

dependencies {
  api(project(":testing-common"))

  implementation(project(":instrumentation-api"))
  implementation(project(":instrumentation-api-incubator"))
}

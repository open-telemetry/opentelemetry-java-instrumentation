/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.gradle

import org.gradle.api.Project

/** Returns true when the build is run with `-PtestLatestDeps=true`. */
val Project.testLatestDeps: Boolean
  get() = gradle.startParameter.projectProperties["testLatestDeps"] == "true"

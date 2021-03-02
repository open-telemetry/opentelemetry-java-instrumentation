/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.testing.LibraryTestRunner
/**
 * A trait which initializes instrumentation library tests, including a test span exporter. All
 * library tests should implement this trait.
 */
trait LibraryTestTrait {
  // library test runner has to be initialized statically so that GlobalOpenTelemetry is set as soon as possible
  private static final LibraryTestRunner RUNNER = LibraryTestRunner.instance()

  LibraryTestRunner testRunner() {
    RUNNER
  }

  OpenTelemetry getOpenTelemetry() {
    RUNNER.openTelemetrySdk
  }
}

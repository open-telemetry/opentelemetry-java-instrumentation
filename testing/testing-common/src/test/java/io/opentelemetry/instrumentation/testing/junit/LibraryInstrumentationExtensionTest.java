/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.extension.RegisterExtension;

class LibraryInstrumentationExtensionTest {

  static {
    // TODO: remove once test dependency on javaagent-tooling is removed
    System.setProperty("io.opentelemetry.context.contextStorageProvider", "default");
  }

  @RegisterExtension
  static final LibraryInstrumentationExtension testing = LibraryInstrumentationExtension.create();

  // repeated test verifies that the telemetry data is cleared between test runs
  @RepeatedTest(5)
  void shouldCollectTraces() {
    // when
    testing.runWithSpan(
        "parent",
        () -> {
          testing.runWithSpan("child", () -> {});
          return null;
        });

    // then
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                parentSpan -> parentSpan.hasName("parent"),
                childSpan -> childSpan.hasName("child")));
  }
}

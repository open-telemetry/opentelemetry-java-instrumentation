/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit;

import static io.opentelemetry.sdk.testing.assertj.TracesAssert.assertThat;

import io.opentelemetry.instrumentation.test.utils.TraceUtils;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.extension.RegisterExtension;

class LibraryInstrumentationExtensionTest {

  static {
    // TODO: remove once test depedency on javaagent-tooling is removed
    System.setProperty("io.opentelemetry.context.contextStorageProvider", "default");
  }

  @RegisterExtension
  static final LibraryInstrumentationExtension testing = LibraryInstrumentationExtension.create();

  // repeated test verifies that the telemetry data is cleared between test runs
  @RepeatedTest(5)
  void shouldCollectTraces() throws TimeoutException, InterruptedException {
    // when
    testing.runWithSpan(
        "parent",
        () -> {
          TraceUtils.runInternalSpan("child");
          return null;
        });

    // then
    List<List<SpanData>> traces = testing.waitForTraces(1);
    assertThat(traces)
        .hasSize(1)
        .hasTracesSatisfyingExactly(
            trace ->
                trace
                    .hasSize(2)
                    .hasSpansSatisfyingExactly(
                        parentSpan -> parentSpan.hasName("parent"),
                        childSpan -> childSpan.hasName("child")));
  }
}

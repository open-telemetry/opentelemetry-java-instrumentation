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
  @RegisterExtension
  static final LibraryInstrumentationExtension instrumentation =
      LibraryInstrumentationExtension.create();

  // repeated test verifies that the telemetry data is cleared between test runs
  @RepeatedTest(5)
  void shouldCollectTraces() throws TimeoutException, InterruptedException {
    // when
    TraceUtils.runUnderTrace(
        "parent",
        () -> {
          TraceUtils.runInternalSpan("child");
          return null;
        });

    // then
    List<List<SpanData>> traces = instrumentation.waitForTraces(1);
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

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.opentelemetry.instrumentation.test.utils.TraceUtils;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.extension.RegisterExtension;

class LibraryInstrumentationExtensionTest {
  @RegisterExtension
  static final LibraryInstrumentationExtension instrumentation =
      LibraryInstrumentationExtension.create();

  // repeated test verifies that the telemetry data is cleared between test runs
  @RepeatedTest(5)
  void shouldCollectTraces() {
    // when
    TraceUtils.runInternalSpan("test");

    // then
    List<SpanData> spans = instrumentation.spans();
    assertEquals(1, spans.size());
    assertThat(spans.get(0)).hasName("test");
  }
}

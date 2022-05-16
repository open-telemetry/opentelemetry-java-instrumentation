/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.opentelemetry.api.trace.StatusCode;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultSpanStatusExtractorTest {

  @Mock SpanStatusBuilder spanStatusBuilder;

  @Test
  void noException() {
    SpanStatusExtractor.getDefault()
        .extract(spanStatusBuilder, Collections.emptyMap(), Collections.emptyMap(), null);
    verifyNoInteractions(spanStatusBuilder);
  }

  @Test
  void exception() {
    SpanStatusExtractor.getDefault()
        .extract(
            spanStatusBuilder,
            Collections.emptyMap(),
            Collections.emptyMap(),
            new IllegalStateException("test"));
    verify(spanStatusBuilder).setStatus(StatusCode.ERROR, "java.lang.IllegalStateException: test");
  }
}

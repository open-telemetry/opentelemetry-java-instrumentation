/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.api.tracer.HttpStatusConverter;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HttpSpanStatusExtractorTest {
  @Mock private HttpAttributesExtractor<Map<String, String>, Map<String, String>> extractor;

  @ParameterizedTest
  @ValueSource(longs = {1, 100, 101, 200, 201, 300, 301, 400, 401, 500, 501, 600, 601})
  void hasStatus(long statusCode) {
    when(extractor.statusCode(anyMap(), anyMap())).thenReturn(statusCode);

    assertThat(
            SpanStatusExtractor.http(extractor)
                .extract(Collections.emptyMap(), Collections.emptyMap(), null))
        .isEqualTo(HttpStatusConverter.statusFromHttpStatus((int) statusCode));
  }

  @ParameterizedTest
  @ValueSource(longs = {1, 100, 101, 200, 201, 300, 301, 400, 401, 500, 501, 600, 601})
  void hasStatus_ignoresException(long statusCode) {
    when(extractor.statusCode(anyMap(), anyMap())).thenReturn(statusCode);

    // Presence of exception has no effect.
    assertThat(
            SpanStatusExtractor.http(extractor)
                .extract(
                    Collections.emptyMap(), Collections.emptyMap(), new IllegalStateException()))
        .isEqualTo(HttpStatusConverter.statusFromHttpStatus((int) statusCode));
  }

  @Test
  void fallsBackToDefault_unset() {
    when(extractor.statusCode(anyMap(), anyMap())).thenReturn(null);

    assertThat(
            SpanStatusExtractor.http(extractor)
                .extract(Collections.emptyMap(), Collections.emptyMap(), null))
        .isEqualTo(StatusCode.UNSET);
  }

  @Test
  void fallsBackToDefault_error() {
    when(extractor.statusCode(anyMap(), anyMap())).thenReturn(null);

    assertThat(
            SpanStatusExtractor.http(extractor)
                .extract(
                    Collections.emptyMap(), Collections.emptyMap(), new IllegalStateException()))
        .isEqualTo(StatusCode.ERROR);
  }
}

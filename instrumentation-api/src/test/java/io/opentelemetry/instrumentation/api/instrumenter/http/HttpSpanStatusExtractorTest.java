/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.trace.SpanKind;
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
  @Mock private HttpCommonAttributesExtractor<Map<String, String>, Map<String, String>> extractor;

  @ParameterizedTest
  @ValueSource(ints = {1, 100, 101, 200, 201, 300, 301, 400, 401, 500, 501, 600, 601})
  void clientSpanHasStatus(int statusCode) {
    when(extractor.statusCode(anyMap(), anyMap())).thenReturn(statusCode);
    assertThat(
            HttpSpanStatusExtractor.create(extractor)
                .extract(Collections.emptyMap(), Collections.emptyMap(), SpanKind.CLIENT, null))
        .isEqualTo(HttpStatusConverter.statusFromHttpStatus(statusCode, SpanKind.CLIENT));
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 100, 101, 200, 201, 300, 301, 400, 401, 500, 501, 600, 601})
  void serverSpanHasStatus(int statusCode) {
    when(extractor.statusCode(anyMap(), anyMap())).thenReturn(statusCode);
    assertThat(
            HttpSpanStatusExtractor.create(extractor)
                .extract(Collections.emptyMap(), Collections.emptyMap(), SpanKind.SERVER, null))
        .isEqualTo(HttpStatusConverter.statusFromHttpStatus(statusCode, SpanKind.SERVER));
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 100, 101, 200, 201, 300, 301, 400, 401, 500, 501, 600, 601})
  void hasStatusAndException(int statusCode) {
    when(extractor.statusCode(anyMap(), anyMap())).thenReturn(statusCode);

    // Presence of exception has no effect.
    assertThat(
            HttpSpanStatusExtractor.create(extractor)
                .extract(
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    SpanKind.SERVER,
                    new IllegalStateException()))
        .isEqualTo(StatusCode.ERROR);
  }

  @Test
  void hasNoStatus_fallsBackToDefault_unset() {
    when(extractor.statusCode(anyMap(), anyMap())).thenReturn(null);

    assertThat(
            HttpSpanStatusExtractor.create(extractor)
                .extract(Collections.emptyMap(), Collections.emptyMap(), SpanKind.SERVER, null))
        .isEqualTo(StatusCode.UNSET);
  }

  @Test
  void hasNoStatus_fallsBackToDefault_error() {
    when(extractor.statusCode(anyMap(), anyMap())).thenReturn(null);

    assertThat(
            HttpSpanStatusExtractor.create(extractor)
                .extract(
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    SpanKind.SERVER,
                    new IllegalStateException()))
        .isEqualTo(StatusCode.ERROR);
  }
}

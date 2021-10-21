/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

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
  @Mock
  private HttpServerAttributesExtractor<Map<String, String>, Map<String, String>> serverExtractor;

  @Mock
  private HttpClientAttributesExtractor<Map<String, String>, Map<String, String>> clientExtractor;

  @ParameterizedTest
  @ValueSource(ints = {1, 100, 101, 200, 201, 300, 301, 500, 501, 600, 601})
  void hasServerStatus(int statusCode) {
    when(serverExtractor.statusCode(anyMap(), anyMap())).thenReturn(statusCode);
    assertThat(
            HttpSpanStatusExtractor.create(serverExtractor)
                .extract(Collections.emptyMap(), Collections.emptyMap(), null))
        .isEqualTo(HttpStatusConverter.SERVER.statusFromHttpStatus(statusCode));
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 100, 101, 200, 201, 300, 301, 400, 401, 500, 501, 600, 601})
  void hasClientStatus(int statusCode) {
    when(clientExtractor.statusCode(anyMap(), anyMap())).thenReturn(statusCode);
    assertThat(
            HttpSpanStatusExtractor.create(clientExtractor)
                .extract(Collections.emptyMap(), Collections.emptyMap(), null))
        .isEqualTo(HttpStatusConverter.CLIENT.statusFromHttpStatus(statusCode));
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 100, 101, 200, 201, 300, 301, 400, 401, 500, 501, 600, 601})
  void hasServerStatusAndException(int statusCode) {
    when(serverExtractor.statusCode(anyMap(), anyMap())).thenReturn(statusCode);

    // Presence of exception has no effect.
    assertThat(
            HttpSpanStatusExtractor.create(serverExtractor)
                .extract(
                    Collections.emptyMap(), Collections.emptyMap(), new IllegalStateException()))
        .isEqualTo(StatusCode.ERROR);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 100, 101, 200, 201, 300, 301, 400, 401, 500, 501, 600, 601})
  void hasClientStatusAndException(int statusCode) {
    when(clientExtractor.statusCode(anyMap(), anyMap())).thenReturn(statusCode);

    // Presence of exception has no effect.
    assertThat(
            HttpSpanStatusExtractor.create(clientExtractor)
                .extract(
                    Collections.emptyMap(), Collections.emptyMap(), new IllegalStateException()))
        .isEqualTo(StatusCode.ERROR);
  }

  @Test
  void hasNoStatus_fallsBackToDefault_unset() {
    when(clientExtractor.statusCode(anyMap(), anyMap())).thenReturn(null);
    when(serverExtractor.statusCode(anyMap(), anyMap())).thenReturn(null);

    assertThat(
            HttpSpanStatusExtractor.create(serverExtractor)
                .extract(Collections.emptyMap(), Collections.emptyMap(), null))
        .isEqualTo(StatusCode.UNSET);
    assertThat(
            HttpSpanStatusExtractor.create(clientExtractor)
                .extract(Collections.emptyMap(), Collections.emptyMap(), null))
        .isEqualTo(StatusCode.UNSET);
  }

  @Test
  void hasNoStatus_fallsBackToDefault_error() {
    when(clientExtractor.statusCode(anyMap(), anyMap())).thenReturn(null);
    when(serverExtractor.statusCode(anyMap(), anyMap())).thenReturn(null);

    StatusCode serverStatusCode =
        HttpSpanStatusExtractor.create(serverExtractor)
            .extract(Collections.emptyMap(), Collections.emptyMap(), new IllegalStateException());
    assertThat(serverStatusCode).isEqualTo(StatusCode.ERROR);

    StatusCode clientStatusCode =
        HttpSpanStatusExtractor.create(clientExtractor)
            .extract(Collections.emptyMap(), Collections.emptyMap(), new IllegalStateException());
    assertThat(clientStatusCode).isEqualTo(StatusCode.ERROR);
  }
}

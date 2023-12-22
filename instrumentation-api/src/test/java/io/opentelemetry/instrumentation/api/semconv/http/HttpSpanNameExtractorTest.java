/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HttpSpanNameExtractorTest {

  @Mock private HttpClientAttributesGetter<Map<String, String>, Map<String, String>> clientGetter;

  @Mock private HttpServerAttributesGetter<Map<String, String>, Map<String, String>> serverGetter;

  @Test
  void routeAndMethod() {
    when(serverGetter.getHttpRoute(anyMap())).thenReturn("/cats/{id}");
    when(serverGetter.getHttpRequestMethod(anyMap())).thenReturn("GET");
    assertThat(HttpSpanNameExtractor.create(serverGetter).extract(Collections.emptyMap()))
        .isEqualTo("GET /cats/{id}");
  }

  @Test
  void method() {
    when(clientGetter.getHttpRequestMethod(anyMap())).thenReturn("GET");
    assertThat(HttpSpanNameExtractor.create(clientGetter).extract(Collections.emptyMap()))
        .isEqualTo("GET");
  }

  @Test
  void nothing() {
    assertThat(HttpSpanNameExtractor.create(clientGetter).extract(Collections.emptyMap()))
        .isEqualTo("HTTP");
  }
}

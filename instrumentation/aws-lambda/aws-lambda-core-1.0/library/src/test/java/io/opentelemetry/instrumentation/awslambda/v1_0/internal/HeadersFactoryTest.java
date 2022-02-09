/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HeadersFactoryTest {

  @Test
  public void shouldReadHeadersFromStream() {
    // given
    String json =
        "{"
            + "\"headers\" : {"
            + "\"X-B3-TraceId\": \"4fd0b6131f19f39af59518d127b0cafe\", \"X-B3-SpanId\": \"0000000000000456\", \"X-B3-Sampled\": \"true\""
            + "},"
            + "\"body\" : \"hello\""
            + "}";
    InputStream inputStream = new ByteArrayInputStream(json.getBytes(Charset.defaultCharset()));
    // when
    Map<String, String> headers = HeadersFactory.ofStream(inputStream);
    // then
    assertThat(headers).isNotNull();
    assertThat(headers.size()).isEqualTo(3);
    assertThat(headers)
        .containsOnly(
            entry("X-B3-TraceId", "4fd0b6131f19f39af59518d127b0cafe"),
            entry("X-B3-SpanId", "0000000000000456"),
            entry("X-B3-Sampled", "true"));
  }

  @Test
  public void shouldReturnNullIfNoHeadersInStream() {
    // given
    String json = "{\"something\" : \"else\"}";
    InputStream inputStream = new ByteArrayInputStream(json.getBytes(Charset.defaultCharset()));
    // when
    Map<String, String> headers = HeadersFactory.ofStream(inputStream); // then
    assertThat(headers).isEmpty();
  }
}

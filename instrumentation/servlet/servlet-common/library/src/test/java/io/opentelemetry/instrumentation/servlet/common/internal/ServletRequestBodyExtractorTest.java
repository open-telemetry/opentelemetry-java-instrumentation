/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.common.internal;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ServletRequestBodyExtractorTest {

  @Test
  void notABuffer() {
    assertThat(ServletRequestBodyExtractor.decode(null, null)).isNull();
    assertThat(ServletRequestBodyExtractor.decode("body", null)).isNull();
  }

  @Test
  void emptyBuffer() {
    assertThat(ServletRequestBodyExtractor.decode(ByteBuffer.allocate(10), null)).isNull();
  }

  @Test
  void directBuffer() {
    ByteBuffer buffer = ByteBuffer.allocateDirect(10);
    buffer.put((byte) 'a');

    // only array-backed buffers are supported
    assertThat(buffer.hasArray()).isFalse();

    assertThat(ServletRequestBodyExtractor.decode(buffer, null)).isNull();
  }

  @Test
  void usesCapturedBytesOnly() {
    // the buffer is larger than the captured content
    ByteBuffer buffer = newBuffer("request-body", UTF_8, 64);
    assertThat(ServletRequestBodyExtractor.decode(buffer, null)).isEqualTo("request-body");
  }

  @Test
  void nonUtf8ContentWithCharset() {
    ByteBuffer buffer = newBuffer("café", ISO_8859_1, 16);
    assertThat(ServletRequestBodyExtractor.decode(buffer, "ISO-8859-1")).isEqualTo("café");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"not-a-charset"})
  void utf8ContentWithUnknownCharset(String charsetName) {
    ByteBuffer buffer = newBuffer("café", UTF_8, 16);
    assertThat(ServletRequestBodyExtractor.decode(buffer, charsetName)).isEqualTo("café");
  }

  @Test
  void nonUtf8ContentWithUnknownCharset() {
    // this documents the current behavior: the content is assumed to be UTF-8 when the charset is
    // unknown. This is not a 'feature' and we can improve automatic detection in the future if this
    // becomes a common issue.
    ByteBuffer buffer = newBuffer("café", ISO_8859_1, 16);
    assertThat(ServletRequestBodyExtractor.decode(buffer, null)).isEqualTo("caf�");
  }

  private static ByteBuffer newBuffer(String content, Charset charset, int capacity) {
    ByteBuffer buffer = ByteBuffer.allocate(capacity);
    buffer.put(content.getBytes(charset));
    assertThat(buffer.hasArray()).isTrue();
    return buffer;
  }
}

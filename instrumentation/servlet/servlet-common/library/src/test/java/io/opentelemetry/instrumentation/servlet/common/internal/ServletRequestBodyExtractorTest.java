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

  private static ByteBuffer captured(String content, Charset charset, int capacity) {
    ByteBuffer buffer = ByteBuffer.allocate(capacity);
    buffer.put(content.getBytes(charset));
    return buffer;
  }

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

    assertThat(ServletRequestBodyExtractor.decode(buffer, null)).isNull();
  }

  @Test
  void usesCapturedBytesOnly() {
    // the buffer is larger than the captured content
    ByteBuffer buffer = captured("request-body", UTF_8, 64);

    assertThat(ServletRequestBodyExtractor.decode(buffer, null)).isEqualTo("request-body");
  }

  @Test
  void bufferWithArrayOffset() {
    ByteBuffer base = ByteBuffer.allocate(32);
    base.position(2);
    // a slice shares the array of the base buffer, but starts at an offset
    ByteBuffer buffer = base.slice();
    buffer.put("body".getBytes(UTF_8));

    assertThat(buffer.arrayOffset()).isEqualTo(2);
    assertThat(ServletRequestBodyExtractor.decode(buffer, null)).isEqualTo("body");
  }

  @Test
  void charset() {
    ByteBuffer buffer = captured("café", ISO_8859_1, 16);

    assertThat(ServletRequestBodyExtractor.decode(buffer, "ISO-8859-1")).isEqualTo("café");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"not-a-charset"})
  void defaultsToUtf8(String charsetName) {
    ByteBuffer buffer = captured("café", UTF_8, 16);

    assertThat(ServletRequestBodyExtractor.decode(buffer, charsetName)).isEqualTo("café");
  }

  @Test
  void bufferIsNotModified() {
    ByteBuffer buffer = captured("request-body", UTF_8, 64);
    int position = buffer.position();
    int limit = buffer.limit();

    assertThat(ServletRequestBodyExtractor.decode(buffer, null)).isEqualTo("request-body");
    // can be decoded several times, and capture can go on
    assertThat(ServletRequestBodyExtractor.decode(buffer, null)).isEqualTo("request-body");
    assertThat(buffer.position()).isEqualTo(position);
    assertThat(buffer.limit()).isEqualTo(limit);
  }
}

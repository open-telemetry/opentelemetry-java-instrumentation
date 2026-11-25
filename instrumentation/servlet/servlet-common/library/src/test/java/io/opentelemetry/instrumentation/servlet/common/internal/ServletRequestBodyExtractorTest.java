/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.common.internal;

import static io.opentelemetry.instrumentation.servlet.common.internal.BodyCaptureConfig.createDefault;
import static io.opentelemetry.instrumentation.servlet.common.internal.BodyCaptureHelper.REQUEST_BODY_BUFFER_ATTRIBUTE;
import static io.opentelemetry.instrumentation.servlet.common.internal.BodyCaptureHelper.REQUEST_BODY_MARKER_ATTRIBUTE;
import static io.opentelemetry.instrumentation.servlet.common.internal.ServletRequestBodyExtractor.extractRequestBody;
import static io.opentelemetry.instrumentation.servlet.common.internal.ServletRequestBodyExtractor.getAndNormalizeMediaTypeFromContentTypeHeader;
import static io.opentelemetry.instrumentation.servlet.common.internal.ServletRequestBodyExtractor.getCharsetFromContentTypeHeader;
import static io.opentelemetry.instrumentation.servlet.common.internal.ServletRequestBodyExtractor.getRequestBodyValue;
import static io.opentelemetry.instrumentation.servlet.common.internal.ServletRequestBodyExtractor.shouldMarkRequestForBodyCapture;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ServletRequestBodyExtractorTest {

  private static final byte[] ISO_8859_1_BODY = "café".getBytes(ISO_8859_1);
  // default enabled config
  private static final BodyCaptureConfig CONFIG = createDefault().setEnabled(true);

  private final Object request = new Object();
  private final Map<String, Object> requestAttributes = new HashMap<>();
  private String requestContentType = null;

  @SuppressWarnings("unchecked")
  private final ServletAccessor<Object, Object> accessor = mock(ServletAccessor.class);

  @BeforeEach
  void setUp() {
    when(accessor.getRequestAttribute(any(), anyString()))
        .thenAnswer(invocation -> requestAttributes.get(invocation.<String>getArgument(1)));
    doAnswer(
            invocation -> {
              String name = invocation.getArgument(1);
              Object value = invocation.getArgument(2);
              // same contract as ServletRequest#setAttribute: null removes the attribute
              if (value == null) {
                requestAttributes.remove(name);
              } else {
                requestAttributes.put(name, value);
              }
              return null;
            })
        .when(accessor)
        .setRequestAttribute(any(), anyString(), any());

    when(accessor.getRequestHeader(any(), eq("Content-Type")))
        .thenAnswer(invocation -> requestContentType);
  }

  @Test
  void noBodyAttribute() {
    assertThat(extractRequestBody(accessor, request)).isNull();
    assertThat(requestAttributes).isEmpty();
  }

  @Test
  void notABuffer() {
    requestAttributes.put(REQUEST_BODY_BUFFER_ATTRIBUTE, "body");
    requestAttributes.put(REQUEST_BODY_MARKER_ATTRIBUTE, true);

    assertThat(extractRequestBody(accessor, request)).isNull();
  }

  @Test
  void emptyBuffer() {
    ByteBuffer buffer = ByteBuffer.allocate(10);
    requestAttributes.put(REQUEST_BODY_BUFFER_ATTRIBUTE, buffer);
    requestAttributes.put(REQUEST_BODY_MARKER_ATTRIBUTE, true);

    assertThat(extractRequestBody(accessor, request)).isNull();

    assertThat(requestAttributes).doesNotContainKey(REQUEST_BODY_BUFFER_ATTRIBUTE);
  }

  @Test
  void bodyExtractedOnce() {

    ByteBuffer buffer = bufferOf("abc".getBytes(UTF_8));
    requestAttributes.put(REQUEST_BODY_BUFFER_ATTRIBUTE, buffer);
    requestAttributes.put(REQUEST_BODY_MARKER_ATTRIBUTE, true);
    requestContentType = "text/plain; charset=UTF-8";

    assertThat(extractRequestBody(accessor, request)).isEqualTo("abc");
    assertThat(extractRequestBody(accessor, request)).isNull();
  }

  @Test
  void withoutContentTypeNoCapture() {
    assertThat(shouldMarkRequestForBodyCapture(CONFIG, null, null)).isFalse();

    assertThat(getRequestBodyValue(bufferOf(ISO_8859_1_BODY), null)).isNull();
    assertThat(getRequestBodyValue(bufferOf("request-body".getBytes(UTF_8)), null)).isNull();
  }

  @Test
  void contentEncodingFiltering() {
    assertThat(shouldMarkRequestForBodyCapture(CONFIG, "text/plain", null)).isTrue();
    assertThat(shouldMarkRequestForBodyCapture(CONFIG, "text/plain", "identity")).isTrue();
    assertThat(shouldMarkRequestForBodyCapture(CONFIG, "text/plain", "gzip")).isFalse();
  }

  @Test
  void nonUtf8WithContentTypeWithEncoding() {
    String contentType = "text/plain; charset=ISO-8859-1";
    assertThat(shouldMarkRequestForBodyCapture(CONFIG, contentType, null)).isTrue();
    assertThat(getRequestBodyValue(bufferOf(ISO_8859_1_BODY), "text/plain; charset=ISO-8859-1"))
        .isEqualTo("café");
  }

  @ParameterizedTest
  @CsvSource(
      delimiterString = "|",
      value = {
        "text/plain; charset=ISO-8859-1|ISO-8859-1",
        "text/plain;charset=iso-8859-1|ISO-8859-1",
        "text/plain; charset=iso-8859-1 |ISO-8859-1",
        "text/plain; charset=\"ISO-8859-1\"|ISO-8859-1",
        "text/plain; charset=iso-8859-1; boundary=x|ISO-8859-1",
        "text/plain; charset=latin1|ISO-8859-1",
        "text/plain; charset=utf8|UTF-8",
        "text/plain; charset=UTF-8|UTF-8"
      })
  void charsetDecoding(String contentType, String expectedCharset) {
    Charset expected = Charset.forName(expectedCharset);
    assertThat(getCharsetFromContentTypeHeader(contentType)).isEqualTo(expected);
  }

  @Test
  void unknownCharsetAssumedUtf8() {
    assertThat(getCharsetFromContentTypeHeader("text/plain; charset=unknown")).isEqualTo(UTF_8);
    assertThat(getRequestBodyValue(bufferOf("café".getBytes(UTF_8)), "text/plain; charset=unknown"))
        .isEqualTo("café");
  }

  @Test
  void truncatedUtf8() {
    ByteBuffer buffer = ByteBuffer.allocate(6);
    buffer.put("café".getBytes(UTF_8)).put((byte) 0xC3); // truncated UTF-8 sequence

    assertThat(shouldMarkRequestForBodyCapture(CONFIG, "text/plain", null)).isTrue();
    assertThat(getRequestBodyValue(buffer, "text/plain")).isEqualTo("café");
  }

  @Test
  void invalidByteInTheMiddleStopsDecoding() {
    ByteBuffer buffer = ByteBuffer.allocate(16);
    buffer.put("abc".getBytes(UTF_8)).put((byte) 0xFF).put("def".getBytes(UTF_8));

    assertThat(getRequestBodyValue(buffer, "text/plain")).isEqualTo("abc");
  }

  @Test
  void invalidByteAndTruncationKeepsTextBeforeFirstInvalidByte() {
    ByteBuffer buffer = ByteBuffer.allocate(16);
    buffer.put("café".getBytes(UTF_8)).put((byte) 0xFF).put("é".getBytes(UTF_8)).put((byte) 0xC3);

    assertThat(getRequestBodyValue(buffer, "text/plain")).isEqualTo("café");
  }

  @Test
  void noReplacementCharacterAdded() {
    ByteBuffer buffer = ByteBuffer.allocate(16);
    buffer.put("a".getBytes(UTF_8)).put((byte) 0xC3).put((byte) 0x28).put("b".getBytes(UTF_8));

    assertThat(getRequestBodyValue(buffer, "text/plain"))
        .isEqualTo("a")
        .doesNotContain(String.valueOf((char) 0xFFFD)) // unicode replacement character
        .doesNotContain("?");
  }

  @ParameterizedTest
  @CsvSource(
      delimiterString = "|",
      nullValues = {"null"},
      value = {
        // should be normalized to lower case for comparison
        "APPLICATION/JSON|application/json",
        // empty or invalid should be ignored
        "|null",
        " ;|null",
        " ; charset=UTF-8|null",
        // with or without charset and other parameters
        "text/plain|text/plain",
        "text/plain;|text/plain",
        "text/plain ;|text/plain",
        "text/plain; charset=ISO-8859-1|text/plain"
      })
  void mediaTypeNormalization(String contentType, @Nullable String expectedMediaType) {
    assertThat(getAndNormalizeMediaTypeFromContentTypeHeader(contentType))
        .isEqualTo(expectedMediaType);
  }

  @Test
  void mediaTypeNotAllowed() {
    ByteBuffer buffer = bufferOf("abc".getBytes(UTF_8));
    requestAttributes.put(REQUEST_BODY_BUFFER_ATTRIBUTE, buffer);
    requestContentType = "application/octet-stream";

    // media type filtering is only applied on request start
    assertThat(shouldMarkRequestForBodyCapture(CONFIG, requestContentType, null)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"multipart/form-data; boundary=xxx", "MULTIPART/MIXED", "multipart/x-foo"})
  void multipartNeverCaptured(String contentType) {
    BodyCaptureConfig config =
        BodyCaptureConfig.createDefault()
            .setEnabled(true)
            .setMediaTypes(IncludeExclude.builder().setIncluded("*/*", "multipart/*").build());

    assertThat(shouldMarkRequestForBodyCapture(config, contentType, null)).isFalse();
    // other media types are still captured with the same configuration
    assertThat(shouldMarkRequestForBodyCapture(config, "application/octet-stream", null)).isTrue();
  }

  @Test
  void formUrlEncodedNotCapturedByDefaultButCanBeEnabled() {
    String contentType = "application/x-www-form-urlencoded";
    assertThat(shouldMarkRequestForBodyCapture(CONFIG, contentType, null)).isFalse();

    BodyCaptureConfig config =
        BodyCaptureConfig.createDefault()
            .setEnabled(true)
            .setMediaTypes(IncludeExclude.builder().setIncluded(contentType).build());
    assertThat(shouldMarkRequestForBodyCapture(config, contentType, null)).isTrue();
  }

  @Test
  void mediaTypeNotAllowedByConfiguration() {
    BodyCaptureConfig config =
        createDefault()
            .setEnabled(true)
            .setMediaTypes(IncludeExclude.builder().setIncluded("text/*").build());

    // media type filtering is only applied on request start
    assertThat(shouldMarkRequestForBodyCapture(config, "application/json", null)).isFalse();
  }

  private static ByteBuffer bufferOf(byte[] bytes) {
    ByteBuffer buffer = ByteBuffer.allocate(bytes.length);
    buffer.put(bytes);
    return buffer;
  }
}

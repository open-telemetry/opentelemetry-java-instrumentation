/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.common.internal;

import static io.opentelemetry.instrumentation.servlet.common.internal.BodyCaptureHelper.REQUEST_BODY_ATTRIBUTE;
import static io.opentelemetry.instrumentation.servlet.common.internal.ServletRequestBodyExtractor.extractRequestBody;
import static io.opentelemetry.instrumentation.servlet.common.internal.ServletRequestBodyExtractor.getRequestBodyValue;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.Value;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import io.opentelemetry.api.common.ValueType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ServletRequestBodyExtractorTest {

  private final Object request = new Object();
  private final Map<String, Object> requestAttributes = new HashMap<>();

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
  }

  @Test
  void noBodyAttribute() {
    assertThat(extractRequestBody(accessor, request)).isNull();
    assertThat(requestAttributes).isEmpty();
  }

  @Test
  void notABuffer() {
    requestAttributes.put(REQUEST_BODY_ATTRIBUTE, "body");

    assertThat(extractRequestBody(accessor, request)).isNull();

    // attributes that are not a buffer are not ours to remove
    assertThat(requestAttributes).containsEntry(REQUEST_BODY_ATTRIBUTE, "body");
  }

  @Test
  void emptyBuffer() {
    ByteBuffer buffer = ByteBuffer.allocate(10);
    requestAttributes.put(REQUEST_BODY_ATTRIBUTE, buffer);

    assertThat(extractRequestBody(accessor, request)).isNull();

    assertThat(requestAttributes).doesNotContainKey(REQUEST_BODY_ATTRIBUTE);
  }

  @Test
  void bodyExtractedOnce() {
    ByteBuffer buffer = bufferOf("abc".getBytes(UTF_8));
    requestAttributes.put(REQUEST_BODY_ATTRIBUTE, buffer);

    assertThat(extractRequestBody(accessor, request)).isEqualTo(Value.of("abc"));
    assertThat(extractRequestBody(accessor, request)).isNull();
  }

  @Test
  void validUtf8Body() {
    ByteBuffer buffer = bufferOf("request-body".getBytes(UTF_8));

    Value<?> body = getRequestBodyValue(buffer, null);
    assertThat(body).isEqualTo(Value.of("request-body"));
  }

  private static final byte[] ISO_8859_1_BODY = "café".getBytes(ISO_8859_1);

  @Test
  void nonUtf8WithoutContentTypeCapturedAsBinary() {
    assertThat(getRequestBodyValue(bufferOf(ISO_8859_1_BODY), null))
        .isEqualTo(Value.of(ISO_8859_1_BODY));
  }

  @Test
  void nonUtf8WithContentTypeWithoutCharsetCapturedAsBinary() {
    assertThat(getRequestBodyValue(bufferOf(ISO_8859_1_BODY), null))
        .isEqualTo(Value.of(ISO_8859_1_BODY));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "text/plain; charset=ISO-8859-1",
        "text/plain;charset=iso-8859-1",
        "text/plain; charset=\"ISO-8859-1\"",
        "text/plain; charset=ISO-8859-1; boundary=x"
      })
  void nonUtf8WithContentTypeWithEncodingCapturedAsText(String contentType) {
    assertThat(getRequestBodyValue(bufferOf(ISO_8859_1_BODY), contentType))
        .isEqualTo(Value.of("café"));
  }

  @Test
  void unknownCharsetFallsBackToUtf8() {
    assertThat(getRequestBodyValue(bufferOf("café".getBytes(UTF_8)), "text/plain; charset=unknown"))
        .isEqualTo(Value.of("café"));
    assertThat(getRequestBodyValue(bufferOf(ISO_8859_1_BODY), "text/plain; charset=unknown"))
        .isEqualTo(Value.of(ISO_8859_1_BODY));
  }

  private static ByteBuffer bufferOf(byte[] bytes) {
    ByteBuffer buffer = ByteBuffer.allocate(64);
    buffer.put(bytes);
    return buffer;
  }
}

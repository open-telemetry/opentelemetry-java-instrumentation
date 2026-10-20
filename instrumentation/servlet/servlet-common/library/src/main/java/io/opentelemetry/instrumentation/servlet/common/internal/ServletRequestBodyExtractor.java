/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.common.internal;

import static io.opentelemetry.instrumentation.servlet.common.internal.BodyCaptureHelper.REQUEST_BODY_ATTRIBUTE;
import static java.nio.charset.StandardCharsets.UTF_8;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ServletRequestBodyExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<
        ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>> {

  /** Semconv attribute used to store request body in span */
  // TODO: replace this when available in semconv 1.45.0
  public static final AttributeKey<?> SPAN_BODY_ATTRIBUTE =
      AttributeKey.valueKey("http.request.body.content");

  private final ServletAccessor<REQUEST, RESPONSE> accessor;

  public ServletRequestBodyExtractor(ServletAccessor<REQUEST, RESPONSE> accessor) {
    this.accessor = accessor;
  }

  @Override
  public void onStart(
      AttributesBuilder attributes,
      Context parentContext,
      ServletRequestContext<REQUEST> requestServletRequestContext) {}

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      ServletRequestContext<REQUEST> requestServletRequestContext,
      @Nullable ServletResponseContext<RESPONSE> responseServletResponseContext,
      @Nullable Throwable error) {

    String body = getRequestBodyValue(accessor, requestServletRequestContext.request());
    if (body != null) {
      attributes.put(SPAN_BODY_ATTRIBUTE.getKey(), body);
    }
  }

  @Nullable
  public static <REQUEST, RESPONSE> String getRequestBodyValue(
      ServletAccessor<REQUEST, RESPONSE> accessor, REQUEST request) {
    Object bodyAttribute = accessor.getRequestAttribute(request, REQUEST_BODY_ATTRIBUTE);
    if (!(bodyAttribute instanceof ByteBuffer)) {
      return null;
    }
    return decode(bodyAttribute, accessor.getRequestContentEncoding(request));
  }

  /**
   * Decodes the request body captured in the {@link
   * io.opentelemetry.instrumentation.servlet.common.internal.BodyCaptureHelper#REQUEST_BODY_ATTRIBUTE}
   * attribute. This does not depend on the servlet API, so it can also be used from app server
   * instrumentations. The buffer is left untouched, the body can be decoded several times.
   *
   * @param bufferObject {@link ByteBuffer} instance from request attribute
   * @param charsetName charset of the request body, UTF-8 is used when {@code null} or not
   *     supported
   * @return the captured body, or {@code null} when nothing has been captured
   */
  @Nullable
  @SuppressWarnings(
      "ByteBufferBackingArray") // we know the buffer is array-backed and check it at runtime
  public static String decode(@Nullable Object bufferObject, @Nullable String charsetName) {
    if (!(bufferObject instanceof ByteBuffer)) {
      return null;
    }
    ByteBuffer buffer = (ByteBuffer) bufferObject;
    // the buffer is in write mode, the position is the number of bytes captured
    int length = buffer.position();
    if (length == 0 || !buffer.hasArray()) {
      return null;
    }
    Charset charset = UTF_8;
    try {
      if (charsetName != null && Charset.isSupported(charsetName)) {
        charset = Charset.forName(charsetName);
      }
    } catch (IllegalArgumentException e) {
      // silently ignored
    }
    return new String(buffer.array(), buffer.arrayOffset(), length, charset);
  }
}

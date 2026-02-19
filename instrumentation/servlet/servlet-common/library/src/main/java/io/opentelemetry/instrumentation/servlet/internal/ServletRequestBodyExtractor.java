/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.internal;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ServletRequestBodyExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<
        ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>> {

  public static final AttributeKey<String> SPAN_BODY_ATTRIBUTE =
      AttributeKey.stringKey("http.request.body.text");
  public static final String REQUEST_BODY_ATTRIBUTE = "otel.request.body";

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
      attributes.put(SPAN_BODY_ATTRIBUTE, body);
    }
  }

  @Nullable
  @SuppressWarnings(
      "ByteBufferBackingArray") // we know the buffer is array-backed and check it at runtime
  public static <REQUEST, RESPONSE> String getRequestBodyValue(
      ServletAccessor<REQUEST, RESPONSE> accessor, REQUEST request) {
    Object bodyAttribute = accessor.getRequestAttribute(request, REQUEST_BODY_ATTRIBUTE);

    if (bodyAttribute instanceof ByteBuffer) {
      ByteBuffer buffer = (ByteBuffer) bodyAttribute;
      if (buffer.position() == 0) {
        // buffer is empty or has been already flipped and read
        return null;
      }
      String encoding = accessor.getRequestContentEncoding(request);
      Charset charset = StandardCharsets.UTF_8;
      if (encoding != null && Charset.isSupported(encoding)) {
        charset = Charset.forName(encoding);
      }
      if (!buffer.hasArray()) {
        return null;
      }
      buffer.flip();
      String result = new String(buffer.array(), buffer.position(), buffer.limit(), charset);
      buffer.clear();
      return result;
    }
    return null;
  }
}

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
import java.nio.charset.StandardCharsets;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ServletRequestBodyExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<
        ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>> {

  private static final AttributeKey<String> SPAN_BODY_ATTRIBUTE =
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

    Object requestBody =
        accessor.getRequestAttribute(
            requestServletRequestContext.request(), REQUEST_BODY_ATTRIBUTE);

    if (requestBody instanceof ByteBuffer) {
      ByteBuffer buffer = (ByteBuffer) requestBody;
      // TODO: decide how to get the charset/encoding
      buffer.arrayOffset();
      attributes.put(
          SPAN_BODY_ATTRIBUTE,
          new String(buffer.array(), 0, buffer.position(), StandardCharsets.UTF_8));
    }
  }
}

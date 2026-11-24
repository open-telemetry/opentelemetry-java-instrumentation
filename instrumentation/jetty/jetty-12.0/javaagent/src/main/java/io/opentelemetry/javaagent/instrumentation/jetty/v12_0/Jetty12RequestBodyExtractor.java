/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v12_0;

import static io.opentelemetry.instrumentation.servlet.common.internal.BodyCaptureHelper.REQUEST_BODY_BUFFER_ATTRIBUTE;
import static io.opentelemetry.instrumentation.servlet.common.internal.BodyCaptureHelper.REQUEST_BODY_MARKER_ATTRIBUTE;
import static io.opentelemetry.instrumentation.servlet.common.internal.ServletRequestBodyExtractor.SPAN_BODY_ATTRIBUTE;
import static io.opentelemetry.instrumentation.servlet.common.internal.ServletRequestBodyExtractor.getRequestBodyValue;
import static io.opentelemetry.instrumentation.servlet.common.internal.ServletRequestBodyExtractor.shouldMarkRequestForBodyCapture;
import static org.eclipse.jetty.http.HttpHeader.CONTENT_TYPE;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.servlet.common.internal.BodyCaptureConfig;
import java.nio.ByteBuffer;
import javax.annotation.Nullable;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

/**
 * Adds the request body captured by the servlet instrumentation to the server span, when it ends.
 * The body may be read after the servlet has returned, for example when processing is async, so it
 * can't be added when the servlet exits.
 */
class Jetty12RequestBodyExtractor implements AttributesExtractor<Request, Response> {

  private final BodyCaptureConfig config;

  public Jetty12RequestBodyExtractor(BodyCaptureConfig config) {
    this.config = config;
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, Request request) {
    String contentType = request.getHeaders().get("Content-Type");
    String contentDisposition = request.getHeaders().get("Content-Encoding");
    if (!shouldMarkRequestForBodyCapture(config, contentType, contentDisposition)) {
      return;
    }
    request.setAttribute(REQUEST_BODY_MARKER_ATTRIBUTE, true);
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      Request request,
      @Nullable Response response,
      @Nullable Throwable error) {

    if (request.getAttribute(REQUEST_BODY_MARKER_ATTRIBUTE) == null) {
      return;
    }
    request.setAttribute(REQUEST_BODY_MARKER_ATTRIBUTE, null);

    Object bodyAttribute = request.getAttribute(REQUEST_BODY_BUFFER_ATTRIBUTE);
    if (!(bodyAttribute instanceof ByteBuffer buffer)) {
      return;
    }
    request.setAttribute(REQUEST_BODY_BUFFER_ATTRIBUTE, null);

    String contentType = request.getHeaders().get(CONTENT_TYPE);
    String body = getRequestBodyValue(buffer, contentType);
    if (body != null) {
      attributes.put(SPAN_BODY_ATTRIBUTE, body);
    }
  }
}

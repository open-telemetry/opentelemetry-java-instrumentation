/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v5_0.internal;

import static io.opentelemetry.instrumentation.servlet.common.internal.ServletConstants.REQUEST_BODY_ATTRIBUTE;
import static io.opentelemetry.instrumentation.servlet.common.internal.ServletConstants.WRAPPED_ATTRIBUTE;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class Servlet5BodyCaptureRequestWrapper extends HttpServletRequestWrapper {

  private final int maxBufferSize;

  /**
   * Constructs a request object wrapping the given request.
   *
   * @param request the {@link HttpServletRequest} to be wrapped.
   * @throws IllegalArgumentException if the request is null
   */
  public Servlet5BodyCaptureRequestWrapper(HttpServletRequest request, int maxBufferSize) {
    super(request);
    this.maxBufferSize = maxBufferSize;
  }

  @Override
  public ServletInputStream getInputStream() throws IOException {
    ServletInputStream inputStream = super.getInputStream();
    if (inputStream == null
        || inputStream instanceof Servlet5BodyCaptureInputStreamWrapper
        || inputStream.isFinished()) {
      // nothing to capture or already captured
      return inputStream;
    }

    if (getAttribute(REQUEST_BODY_ATTRIBUTE) != null) {
      // The first stream obtained for this request is the one that is captured: later streams are
      // not captured, so that a body read more than once is never recorded twice or overwritten.
      return inputStream;
    }

    ByteBuffer buffer = ByteBuffer.allocate(maxBufferSize);
    setAttribute(REQUEST_BODY_ATTRIBUTE, buffer);
    return new Servlet5BodyCaptureInputStreamWrapper(inputStream, buffer);
  }

  public static HttpServletRequest wrapIfNeeded(HttpServletRequest request, int maxBufferSize) {
    // The request body only needs to be captured once. The instanceof check is a cheap fast path,
    // the attribute also detects our wrapper when it is hidden behind wrappers added by other
    // filters or frameworks.
    if (request instanceof Servlet5BodyCaptureRequestWrapper
        || request.getAttribute(WRAPPED_ATTRIBUTE) != null) {
      return request;
    }
    request.setAttribute(WRAPPED_ATTRIBUTE, true);
    return new Servlet5BodyCaptureRequestWrapper(request, maxBufferSize);
  }
}

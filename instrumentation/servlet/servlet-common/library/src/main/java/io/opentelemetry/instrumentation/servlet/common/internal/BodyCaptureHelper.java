/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.common.internal;

import java.nio.ByteBuffer;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public abstract class BodyCaptureHelper<REQUEST, INPUT> {

  /** Servlet attribute name used to store the request body. */
  public static final String REQUEST_BODY_BUFFER_ATTRIBUTE = "otel.request.body.buffer";

  /** Servlet attribute name used to mark whether the request body capture is enabled on request */
  public static final String REQUEST_BODY_MARKER_ATTRIBUTE = "otel.request.body.marker";

  private final ServletAccessor<REQUEST, ?> accessor;

  public BodyCaptureHelper(ServletAccessor<REQUEST, ?> accessor) {
    this.accessor = accessor;
  }

  protected abstract boolean isInputFinished(INPUT inputStream);

  protected abstract boolean isInputWrapped(INPUT inputStream);

  protected abstract INPUT wrapInput(INPUT inputStream, ByteBuffer buffer);

  /**
   * Checks whether the request is, or wraps, a request that captures its body. Requests can be
   * wrapped by other filters or frameworks, which hide our wrapper.
   */
  protected abstract boolean isRequestWrapped(REQUEST request);

  protected abstract REQUEST wrapRequest(REQUEST request, int maxBufferSize);

  public INPUT getInputStream(REQUEST request, INPUT inputStream, int maxBufferSize) {
    if (inputStream == null || isInputWrapped(inputStream) || isInputFinished(inputStream)) {
      // nothing to capture or already captured
      return inputStream;
    }

    // prevent capturing the body when content-encoding indicates a binary body
    String contentEncoding = accessor.getRequestHeader(request, "Content-Encoding");
    if (contentEncoding != null && !"identity".equalsIgnoreCase(contentEncoding)) {
      return inputStream;
    }

    if (accessor.getRequestAttribute(request, REQUEST_BODY_BUFFER_ATTRIBUTE) != null) {
      // The first stream obtained for this request is the one that is captured: later streams are
      // not captured, so that a body read more than once is never recorded twice or overwritten.
      return inputStream;
    }

    ByteBuffer buffer = ByteBuffer.allocate(maxBufferSize);
    accessor.setRequestAttribute(request, REQUEST_BODY_BUFFER_ATTRIBUTE, buffer);
    return wrapInput(inputStream, buffer);
  }

  public REQUEST wrapIfNeeded(REQUEST request, int maxBufferSize) {
    // The request body only needs to be captured once, so we must wrap only once
    if (isRequestWrapped(request)) {
      return request;
    }
    return wrapRequest(request, maxBufferSize);
  }
}

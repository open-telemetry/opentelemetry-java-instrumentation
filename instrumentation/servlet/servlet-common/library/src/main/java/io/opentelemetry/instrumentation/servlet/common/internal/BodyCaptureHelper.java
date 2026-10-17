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

  /** Servlet attribute used to indicate that the request has been wrapped to capture its body. */
  private static final String WRAPPED_ATTRIBUTE = "otel.request.body.wrapped";

  /** Servlet attribute name used to store the request body. */
  public static final String REQUEST_BODY_ATTRIBUTE = "otel.request.body";

  private final ServletAccessor<REQUEST, ?> accessor;

  public BodyCaptureHelper(ServletAccessor<REQUEST, ?> accessor) {
    this.accessor = accessor;
  }

  protected abstract boolean isInputFinished(INPUT inputStream);

  protected abstract boolean isInputWrapped(INPUT inputStream);

  protected abstract INPUT wrapInput(INPUT inputStream, ByteBuffer buffer);

  protected abstract boolean isRequestWrapped(REQUEST request);

  protected abstract REQUEST wrapRequest(REQUEST request, int maxBufferSize);

  public INPUT getInputStream(REQUEST request, INPUT inputStream, int maxBufferSize) {
    if (inputStream == null || isInputWrapped(inputStream) || isInputFinished(inputStream)) {
      // nothing to capture or already captured
      return inputStream;
    }

    if (accessor.getRequestAttribute(request, REQUEST_BODY_ATTRIBUTE) != null) {
      // The first stream obtained for this request is the one that is captured: later streams are
      // not captured, so that a body read more than once is never recorded twice or overwritten.
      return inputStream;
    }

    ByteBuffer buffer = ByteBuffer.allocate(maxBufferSize);
    accessor.setRequestAttribute(request, REQUEST_BODY_ATTRIBUTE, buffer);
    return wrapInput(inputStream, buffer);
  }

  public REQUEST wrapIfNeeded(REQUEST request, int maxBufferSize) {
    // The request body only needs to be captured once. The instanceof check is a cheap fast path,
    // the attribute also detects our wrapper when it is hidden behind wrappers added by other
    // filters or frameworks.
    if (isRequestWrapped(request)
        || accessor.getRequestAttribute(request, WRAPPED_ATTRIBUTE) != null) {
      return request;
    }
    accessor.setRequestAttribute(request, WRAPPED_ATTRIBUTE, true);
    return wrapRequest(request, maxBufferSize);
  }
}

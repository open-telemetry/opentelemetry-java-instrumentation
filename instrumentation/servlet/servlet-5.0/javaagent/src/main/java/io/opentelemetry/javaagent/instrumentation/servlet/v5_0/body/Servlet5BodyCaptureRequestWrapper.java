/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.body;

import static io.opentelemetry.instrumentation.servlet.internal.ServletRequestBodyExtractor.REQUEST_BODY_ATTRIBUTE;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.IOException;
import java.nio.ByteBuffer;

public class Servlet5BodyCaptureRequestWrapper extends HttpServletRequestWrapper {

  // TODO: make request size configurable
  private static final int MAX_CAPACITY = 100;

  /**
   * Constructs a request object wrapping the given request.
   *
   * @param request the {@link HttpServletRequest} to be wrapped.
   * @throws IllegalArgumentException if the request is null
   */
  public Servlet5BodyCaptureRequestWrapper(HttpServletRequest request) {
    super(request);
  }

  @Override
  public ServletInputStream getInputStream() throws IOException {
    ServletInputStream inputStream = super.getInputStream();
    if (inputStream == null || inputStream.isFinished()) {
      return inputStream;
    }
    ByteBuffer buffer = ByteBuffer.allocate(MAX_CAPACITY);
    super.setAttribute(REQUEST_BODY_ATTRIBUTE, buffer);
    return new Servlet5BodyCaptureInputStreamWrapper(inputStream, buffer);
  }
}

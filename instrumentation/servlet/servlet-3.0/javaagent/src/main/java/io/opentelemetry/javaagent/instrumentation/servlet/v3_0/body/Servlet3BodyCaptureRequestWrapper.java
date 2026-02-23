/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0.body;

import static io.opentelemetry.instrumentation.servlet.internal.ServletRequestBodyExtractor.REQUEST_BODY_ATTRIBUTE;

import java.io.IOException;
import java.nio.ByteBuffer;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

public class Servlet3BodyCaptureRequestWrapper extends HttpServletRequestWrapper {

  private final int maxBufferSize;

  /**
   * Constructs a request object wrapping the given request.
   *
   * @param request the {@link HttpServletRequest} to be wrapped.
   * @throws IllegalArgumentException if the request is null
   */
  public Servlet3BodyCaptureRequestWrapper(HttpServletRequest request, int maxBufferSize) {
    super(request);
    this.maxBufferSize = maxBufferSize;
  }

  @Override
  public ServletInputStream getInputStream() throws IOException {
    ServletInputStream inputStream = super.getInputStream();
    if (inputStream == null) {
      return inputStream;
    }
    ByteBuffer buffer = ByteBuffer.allocate(maxBufferSize);
    setAttribute(REQUEST_BODY_ATTRIBUTE, buffer);
    return new Servlet3BodyCaptureInputStreamWrapper(inputStream, buffer);
  }
}

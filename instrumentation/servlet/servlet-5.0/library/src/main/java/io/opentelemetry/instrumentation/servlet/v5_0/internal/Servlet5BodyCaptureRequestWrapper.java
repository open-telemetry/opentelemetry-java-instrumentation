/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v5_0.internal;

import io.opentelemetry.instrumentation.servlet.common.internal.BodyCaptureHelper;
import io.opentelemetry.instrumentation.servlet.common.internal.CaptureInputStream;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class Servlet5BodyCaptureRequestWrapper extends HttpServletRequestWrapper {

  private static final BodyCaptureHelper<HttpServletRequest, ServletInputStream> helper =
      new BodyCaptureHelper<HttpServletRequest, ServletInputStream>(Servlet5Accessor.INSTANCE) {
        @Override
        protected boolean isInputFinished(ServletInputStream inputStream) {
          return inputStream.isFinished();
        }

        @Override
        protected boolean isInputWrapped(ServletInputStream inputStream) {
          return inputStream instanceof ServletInputStreamWrapper;
        }

        @Override
        protected ServletInputStream wrapInput(ServletInputStream inputStream, ByteBuffer buffer) {
          return new ServletInputStreamWrapper(inputStream, buffer);
        }

        @Override
        protected boolean isRequestWrapped(HttpServletRequest request) {
          return request instanceof Servlet5BodyCaptureRequestWrapper;
        }

        @Override
        protected HttpServletRequest wrapRequest(HttpServletRequest request, int maxBufferSize) {
          return new Servlet5BodyCaptureRequestWrapper(request, maxBufferSize);
        }
      };

  private static class ServletInputStreamWrapper extends ServletInputStream {

    private final ServletInputStream delegate;
    private final InputStream inputStream;

    private ServletInputStreamWrapper(ServletInputStream delegate, ByteBuffer buffer) {
      this.delegate = delegate;
      this.inputStream = new CaptureInputStream(delegate, buffer);
    }

    @Override
    public int read() throws IOException {
      return inputStream.read();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      return inputStream.read(b, off, len);
    }

    @Override
    public int available() throws IOException {
      return delegate.available();
    }

    @Override
    public void close() throws IOException {
      delegate.close();
    }

    @Override
    public boolean isFinished() {
      return delegate.isFinished();
    }

    @Override
    public boolean isReady() {
      return delegate.isReady();
    }

    @Override
    public void setReadListener(ReadListener readListener) {
      delegate.setReadListener(readListener);
    }
  }

  private final int maxBufferSize;

  /**
   * Constructs a request object wrapping the given request.
   *
   * @param request the {@link HttpServletRequest} to be wrapped.
   * @throws IllegalArgumentException if the request is null
   */
  private Servlet5BodyCaptureRequestWrapper(HttpServletRequest request, int maxBufferSize) {
    super(request);
    this.maxBufferSize = maxBufferSize;
  }

  @Override
  public ServletInputStream getInputStream() throws IOException {
    return helper.getInputStream(this, super.getInputStream(), maxBufferSize);
  }

  public static HttpServletRequest wrapIfNeeded(HttpServletRequest request, int maxBufferSize) {
    return helper.wrapIfNeeded(request, maxBufferSize);
  }
}

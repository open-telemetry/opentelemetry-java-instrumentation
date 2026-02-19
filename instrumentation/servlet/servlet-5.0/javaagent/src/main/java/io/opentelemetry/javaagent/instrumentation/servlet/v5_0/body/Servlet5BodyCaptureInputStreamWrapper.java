/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.body;

import io.opentelemetry.instrumentation.servlet.internal.CaptureInputStream;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class Servlet5BodyCaptureInputStreamWrapper extends ServletInputStream {

  private final ServletInputStream delegate;
  private final InputStream inputStream;

  public Servlet5BodyCaptureInputStreamWrapper(ServletInputStream delegate, ByteBuffer buffer) {
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

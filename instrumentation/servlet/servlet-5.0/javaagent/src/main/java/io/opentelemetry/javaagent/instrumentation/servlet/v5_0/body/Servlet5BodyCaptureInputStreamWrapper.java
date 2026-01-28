/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.body;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class Servlet5BodyCaptureInputStreamWrapper extends ServletInputStream {

  private final ServletInputStream inputStream;
  private final ByteBuffer buffer;

  public Servlet5BodyCaptureInputStreamWrapper(ServletInputStream inputStream, ByteBuffer buffer) {
    this.inputStream = inputStream;
    this.buffer = buffer;
  }

  @Override
  public int read() throws IOException {
    int read = inputStream.read();
    if (read > 0 && buffer.hasRemaining()) {
      buffer.put((byte) read);
    }
    return read;
  }

  @Override
  public int read(byte[] b) throws IOException {
    return read(b, 0, b.length);
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    // TODO: handle exceptions by marking content as read
    int read = inputStream.read(b, off, len);
    if (read > 0) {
      int length = Math.min(read, buffer.remaining());
      buffer.put(b, off, length);
    }
    return read;
  }

  @Override
  public int readLine(byte[] b, int off, int len) throws IOException {
    int read = inputStream.readLine(b, off, len);
    if (read > 0) {
      int length = Math.min(read, buffer.remaining());
      buffer.put(b, off, length);
    }
    return read;
  }

  @Override
  public boolean isFinished() {
    return inputStream.isFinished();
  }

  @Override
  public boolean isReady() {
    return inputStream.isReady();
  }

  @Override
  public void setReadListener(ReadListener readListener) {
    inputStream.setReadListener(readListener);
  }
}

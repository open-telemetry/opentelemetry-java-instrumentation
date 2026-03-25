/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * {@link InputStream} wrapper that captures the read data into a {@link ByteBuffer}.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class CaptureInputStream extends InputStream {

  private final InputStream delegate;
  private final ByteBuffer buffer;

  public CaptureInputStream(InputStream delegate, ByteBuffer buffer) {
    this.delegate = delegate;
    this.buffer = buffer;
  }

  @Override
  public int read() throws IOException {
    int read = delegate.read();
    if (read >= 0 && buffer.hasRemaining()) {
      buffer.put((byte) read);
    }
    return read;
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    int read = delegate.read(b, off, len);
    if (read > 0) {
      int length = Math.min(read, buffer.remaining());
      buffer.put(b, off, length);
    }
    return read;
  }
}

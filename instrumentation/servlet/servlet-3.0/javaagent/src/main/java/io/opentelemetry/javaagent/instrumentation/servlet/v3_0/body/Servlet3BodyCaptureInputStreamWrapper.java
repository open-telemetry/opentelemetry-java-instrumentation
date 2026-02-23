/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0.body;

import io.opentelemetry.instrumentation.servlet.internal.CaptureInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import javax.servlet.ServletInputStream;

public class Servlet3BodyCaptureInputStreamWrapper extends ServletInputStream {

  private final InputStream inputStream;

  public Servlet3BodyCaptureInputStreamWrapper(ServletInputStream delegate, ByteBuffer buffer) {
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
}

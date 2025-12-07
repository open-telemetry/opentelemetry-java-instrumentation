/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1.internal;

import java.io.Writer;

// because StringWriter uses the synchronized StringBuffer
class StringBuilderWriter extends Writer {

  private final StringBuilder sb;

  StringBuilderWriter(int initialSize) {
    sb = new StringBuilder(initialSize);
  }

  @Override
  public void write(char[] cbuf, int off, int len) {
    sb.append(cbuf, off, len);
  }

  @Override
  public void flush() {}

  @Override
  public void close() {}

  public StringBuilder getBuilder() {
    return sb;
  }
}

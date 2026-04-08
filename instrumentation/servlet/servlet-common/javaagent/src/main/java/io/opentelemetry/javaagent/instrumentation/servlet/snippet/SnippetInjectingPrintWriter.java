/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.snippet;

import io.opentelemetry.javaagent.bootstrap.servlet.InjectionState;
import io.opentelemetry.javaagent.bootstrap.servlet.SnippetInjectingResponseWrapper;
import java.io.PrintWriter;

public class SnippetInjectingPrintWriter extends PrintWriter {
  private final String snippet;
  private final InjectionState state;

  public SnippetInjectingPrintWriter(
      PrintWriter writer, String snippet, SnippetInjectingResponseWrapper wrapper) {
    super(writer);
    state = new InjectionState(wrapper);
    this.snippet = snippet;
  }

  @Override
  public void write(String s, int off, int len) {
    if (state.isHeadTagWritten()) {
      super.write(s, off, len);
      return;
    }
    String value = String.valueOf(s);
    checkOffsetAndLength(value.length(), off, len);
    for (int i = off; i < off + len; i++) {
      write(value.charAt(i));
    }
  }

  @Override
  public void write(int b) {
    super.write(b);
    if (state.isHeadTagWritten()) {
      return;
    }
    boolean endOfHeadTagFound = state.processByte(b);
    if (!endOfHeadTagFound) {
      return;
    }

    if (state.getWrapper().isNotSafeToInject()) {
      return;
    }
    state.getWrapper().updateContentLengthIfPreviouslySet();
    super.write(snippet);
  }

  @Override
  public void write(char[] buf, int off, int len) {
    if (state.isHeadTagWritten()) {
      super.write(buf, off, len);
      return;
    }
    checkOffsetAndLength(buf.length, off, len);
    for (int i = off; i < off + len; i++) {
      write(buf[i]);
    }
  }

  private static void checkOffsetAndLength(int length, int off, int len) {
    if (off < 0 || len < 0 || off > length - len) {
      throw new IndexOutOfBoundsException();
    }
  }
}

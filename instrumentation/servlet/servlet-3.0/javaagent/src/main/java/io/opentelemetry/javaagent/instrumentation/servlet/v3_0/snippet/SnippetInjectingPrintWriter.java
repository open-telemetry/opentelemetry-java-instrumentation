/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0.snippet;

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
    for (int i = off; i < s.length() && i - off < len; i++) {
      write(s.charAt(i));
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
    for (int i = off; i < buf.length && i - off < len; i++) {
      write(buf[i]);
    }
  }
}

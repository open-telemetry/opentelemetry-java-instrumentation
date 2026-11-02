/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.common.snippet;

import io.opentelemetry.javaagent.bootstrap.servlet.InjectionState;
import io.opentelemetry.javaagent.bootstrap.servlet.SnippetInjectingResponseWrapper;
import java.io.PrintWriter;

public class SnippetInjectingPrintWriter extends PrintWriter {
  private final String snippet;
  private final InjectionState state;

  public SnippetInjectingPrintWriter(
      PrintWriter writer, String snippet, SnippetInjectingResponseWrapper wrapper) {
    this(writer, snippet, new InjectionState(wrapper));
  }

  public SnippetInjectingPrintWriter(PrintWriter writer, String snippet, InjectionState state) {
    super(writer);
    this.state = state;
    state.useWriter();
    this.snippet = snippet;
  }

  @Override
  public void write(String s, int off, int len) {
    if (!state.shouldProcessWrite()) {
      super.write(s, off, len);
      return;
    }
    for (int i = off; i < s.length() && i - off < len; i++) {
      super.write(s.charAt(i));
      processCharacter(s.charAt(i));
    }
  }

  @Override
  public void write(int b) {
    super.write(b);
    if (!state.shouldProcessWrite()) {
      return;
    }
    processCharacter(b);
  }

  @Override
  public void write(char[] buf, int off, int len) {
    if (!state.shouldProcessWrite()) {
      super.write(buf, off, len);
      return;
    }
    for (int i = off; i < buf.length && i - off < len; i++) {
      super.write(buf[i]);
      processCharacter(buf[i]);
    }
  }

  private void processCharacter(int b) {
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
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0.snippet;

import static java.util.logging.Level.FINE;

import io.opentelemetry.javaagent.bootstrap.servlet.SnippetHolder;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.logging.Logger;
import javax.servlet.ServletOutputStream;

public class ServletOutputStreamInjectionHelper {
  private static final Logger logger =
      Logger.getLogger(ServletOutputStreamInjectionHelper.class.getName());

  /**
   * return true means this method performed the injection, return false means it didn't inject
   * anything Servlet3OutputStreamWriteAdvice would skip the write method when the return value is
   * true, and would write the original bytes when the return value is false.
   */
  public static boolean handleWrite(
      byte[] original, int off, int length, InjectionState state, ServletOutputStream out)
      throws IOException {
    if (state.isAlreadyInjected()) {
      return false;
    }
    int i;
    boolean shouldInject = false;
    for (i = off; i < length && i - off < length; i++) {
      if (state.processByte(original[i])) {
        shouldInject = true;
        break;
      }
    }
    if (!shouldInject) {
      return false;
    }
    state.setAlreadyInjected(); // set before write to avoid recursive loop
    out.write(original, off, i + 1);
    try {
      byte[] snippetBytes = SnippetHolder.getSnippetBytes(state.getCharacterEncoding());
      long originalContentLen = state.getWrapper().contentLength;
      if (originalContentLen != 0 && state.getWrapper().isCommitted()) {
        // header already set and sent, stop inject
        return false;
      }
      out.write(snippetBytes);
      state.getWrapper().injected = true;
      if (originalContentLen != 0) {
        // ContentLen already set, need to update it.
        state.getWrapper().setContentLength((int) state.getWrapper().contentLength);
      }
    } catch (UnsupportedEncodingException e) {
      logger.log(FINE, "UnsupportedEncodingException", e);
    }
    out.write(original, i + 1, length - i - 1);
    return true;
  }

  public static boolean handleWrite(InjectionState state, ServletOutputStream out, int b)
      throws IOException {
    if (state.isAlreadyInjected()) {
      return false;
    }
    if (!state.processByte(b)) {
      return false;
    }
    state.setAlreadyInjected(); // set before write to avoid recursive loop
    out.write(b);
    try {
      byte[] snippetBytes = SnippetHolder.getSnippetBytes(state.getCharacterEncoding());
      long originalContentLen = state.getWrapper().contentLength;
      if (originalContentLen != 0 && state.getWrapper().isCommitted()) {
        // header already set and sent, stop inject
        return false;
      }
      out.write(snippetBytes);
      state.getWrapper().injected = true;
      if (originalContentLen != 0) {
        // ContentLen already set, need to update it.
        state.getWrapper().setContentLength((int) state.getWrapper().contentLength);
      }
    } catch (UnsupportedEncodingException e) {
      logger.log(FINE, "UnsupportedEncodingException", e);
    }
    return true;
  }
}

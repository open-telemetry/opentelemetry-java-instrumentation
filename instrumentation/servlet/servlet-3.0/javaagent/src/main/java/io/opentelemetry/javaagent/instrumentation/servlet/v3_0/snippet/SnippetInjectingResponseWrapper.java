/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0.snippet;

import static io.opentelemetry.javaagent.instrumentation.servlet.v3_0.snippet.Injection.initializeInjectionStateIfNeeded;
import static java.util.logging.Level.FINE;

import io.opentelemetry.javaagent.bootstrap.servlet.ExperimentalSnippetHolder;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

public class SnippetInjectingResponseWrapper extends HttpServletResponseWrapper {
  private static final Logger logger = Logger.getLogger(HttpServletResponseWrapper.class.getName());
  public static final String FAKE_SNIPPET_HEADER = "FAKE_SNIPPET_HEADER";
  private static final String SNIPPET = ExperimentalSnippetHolder.getSnippet();
  private static final int SNIPPET_LENGTH = SNIPPET.length();
  @Nullable private static final MethodHandle setContentLengthLongHandler = getMethodHandle();

  private long contentLength = 0;
  private boolean addLengthAlready = false;
  public boolean injected = false;

  private SnippetInjectingPrintWriter snippetInjectingPrintWriter = null;

  public SnippetInjectingResponseWrapper(HttpServletResponse response) {
    super(response);
  }

  private boolean isTimeToAddLength() {
    return injected && !addLengthAlready;
  }

  @Override
  public boolean containsHeader(String name) {
    // override this function in order to make sure the response is wrapped
    // but not wrapped twice
    // we didn't use the traditional method req.setAttribute
    // because async would set the original request attribute but didn't pass down the wrapped
    // response
    // then the response would never be wrapped again
    // see also https://docs.oracle.com/javaee/7/api/javax/servlet/AsyncContext.html
    if (name.equals(FAKE_SNIPPET_HEADER)) {
      return true;
    }
    return super.containsHeader(name);
  }

  @Override
  public void setHeader(String name, String value) {
    if (isContentTypeTextHtml() && "Content-Length".equalsIgnoreCase(name)) {
      if (isTimeToAddLength()) {
        try {
          value = Integer.toString(SNIPPET_LENGTH + Integer.valueOf(value));
          addLengthAlready = true;
        } catch (NumberFormatException ex) {
          logger.log(FINE, "NumberFormatException", ex);
        }
      } else {
        try {
          contentLength = Long.parseLong(Integer.toString(SNIPPET_LENGTH + Integer.valueOf(value)));
        } catch (NumberFormatException ex) {
          logger.log(FINE, "NumberFormatException", ex);
        }
      }
    }
    super.setHeader(name, value);
  }

  @Override
  public void addHeader(String name, String value) {
    if (isContentTypeTextHtml() && "Content-Length".equalsIgnoreCase(name)) {
      if (isTimeToAddLength()) {
        try {
          value = Integer.toString(SNIPPET_LENGTH + Integer.valueOf(value));
          addLengthAlready = true;
        } catch (NumberFormatException ex) {
          logger.log(FINE, "NumberFormatException", ex);
        }
      } else {
        try {
          contentLength = Long.parseLong(Integer.toString(SNIPPET_LENGTH + Integer.valueOf(value)));
        } catch (NumberFormatException ex) {
          logger.log(FINE, "NumberFormatException", ex);
        }
      }
    }
    super.addHeader(name, value);
  }

  @Override
  public void setIntHeader(String name, int value) {
    if (isContentTypeTextHtml() && "Content-Length".equalsIgnoreCase(name)) {
      if (isTimeToAddLength()) {
        value += SNIPPET_LENGTH;
        addLengthAlready = true;
      } else {
        contentLength = value;
      }
    }
    super.setIntHeader(name, value);
  }

  @Override
  public void addIntHeader(String name, int value) {
    if (isContentTypeTextHtml() && "Content-Length".equalsIgnoreCase(name)) {
      if (isTimeToAddLength()) {
        value += SNIPPET_LENGTH;
        addLengthAlready = true;
      } else {
        contentLength = value;
      }
    }
    super.addIntHeader(name, value);
  }

  @Override
  public void setContentLength(int len) {
    if (isContentTypeTextHtml()) {
      if (isTimeToAddLength()) {
        len += SNIPPET_LENGTH;
        addLengthAlready = true;
      } else {
        contentLength = len;
      }
    }
    super.setContentLength(len);
  }

  @Nullable
  private static MethodHandle getMethodHandle() {
    try {
      return MethodHandles.lookup()
          .findSpecial(
              HttpServletResponseWrapper.class,
              "setContentLengthLong",
              MethodType.methodType(void.class),
              SnippetInjectingResponseWrapper.class);
    } catch (NoSuchMethodException | IllegalAccessException e) {
      logger.log(FINE, "SnippetInjectingResponseWrapper setContentLengthLong", e);
      return null;
    }
  }

  public void setContentLengthLong(long length) throws Throwable {
    if (isContentTypeTextHtml()) {
      if (isTimeToAddLength()) {
        length += SNIPPET_LENGTH;
        addLengthAlready = true;
      } else {
        contentLength = length;
      }
    }
    if (setContentLengthLongHandler == null) {
      super.setContentLength((int) length);
    } else {
      setContentLengthLongHandler.invokeWithArguments(this, length);
    }
  }

  public boolean isContentTypeTextHtml() {
    String contentType = super.getContentType();
    if (contentType == null) {
      contentType = super.getHeader("content-type");
    }
    return contentType != null && contentType.startsWith("text/html");
  }

  @Override
  public ServletOutputStream getOutputStream() throws IOException {
    ServletOutputStream output = super.getOutputStream();
    initializeInjectionStateIfNeeded(output, this);
    return output;
  }

  @Override
  public PrintWriter getWriter() throws IOException {
    if (!isContentTypeTextHtml()) {
      return super.getWriter();
    }
    if (snippetInjectingPrintWriter == null) {
      snippetInjectingPrintWriter =
          new SnippetInjectingPrintWriter(super.getWriter(), SNIPPET, this);
    }
    return snippetInjectingPrintWriter;
  }

  public void updateContentLength() {
    setContentLength((int) contentLength);
  }

  public boolean contentLengthWasSet() {
    return contentLength != 0;
  }
}

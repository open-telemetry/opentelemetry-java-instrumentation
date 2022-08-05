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

/**
 * Notes with contentLength: 1. Q: what is the timing that we would update the content length? 1.
 * the injection happened 2. the content length was already set outside 2. Q: Why we would not add
 * the content length after the injection if the content length was not set yet? A: The server may
 * monitor the bytes that have written, and use the new length itself
 */
public class SnippetInjectingResponseWrapper extends HttpServletResponseWrapper {
  private static final Logger logger = Logger.getLogger(HttpServletResponseWrapper.class.getName());
  public static final String FAKE_SNIPPET_HEADER = "FAKE_SNIPPET_HEADER";
  private static final String SNIPPET = ExperimentalSnippetHolder.getSnippet();
  private static final int SNIPPET_LENGTH = SNIPPET.length();
  @Nullable private static final MethodHandle setContentLengthLongHandler = getMethodHandle();

  private long contentLength = -1;

  private SnippetInjectingPrintWriter snippetInjectingPrintWriter = null;

  public SnippetInjectingResponseWrapper(HttpServletResponse response) {
    super(response);
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
    // checking content-type is just an optimization to avoid unnecessary parsing
    if ("Content-Length".equalsIgnoreCase(name) && isContentTypeTextHtml()) {
      try {
        contentLength = Long.valueOf(value);
      } catch (NumberFormatException ex) {
        logger.log(FINE, "NumberFormatException", ex);
      }
    }
    super.setHeader(name, value);
  }

  @Override
  public void addHeader(String name, String value) {
    if ("Content-Length".equalsIgnoreCase(name) && isContentTypeTextHtml()) {
      try {
        contentLength = Long.valueOf(value);
      } catch (NumberFormatException ex) {
        logger.log(FINE, "NumberFormatException", ex);
      }
    }
    super.addHeader(name, value);
  }

  @Override
  public void setIntHeader(String name, int value) {
    if ("Content-Length".equalsIgnoreCase(name) && isContentTypeTextHtml()) {
      contentLength = value;
    }
    super.setIntHeader(name, value);
  }

  @Override
  public void addIntHeader(String name, int value) {
    if ("Content-Length".equalsIgnoreCase(name) && isContentTypeTextHtml()) {
      contentLength = value;
    }
    super.addIntHeader(name, value);
  }

  @Override
  public void setContentLength(int len) {
    contentLength = len;
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
    contentLength = length;
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

  public void updateContentLengthIfPreviouslySet() {
    if (contentLengthWasSet()) {
      setContentLength((int) contentLength + SNIPPET_LENGTH);
    }
  }

  public boolean contentLengthWasSet() {
    return contentLength != -1;
  }

  public boolean isNotSafeToInject() {
    return isCommitted() && contentLengthWasSet();
  }
}

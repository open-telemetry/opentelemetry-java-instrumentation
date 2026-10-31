/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.snippet;

import static io.opentelemetry.javaagent.instrumentation.servlet.v5_0.snippet.ServletOutputStreamInjectionState.initializeInjectionStateIfNeeded;
import static java.util.logging.Level.FINE;

import io.opentelemetry.javaagent.bootstrap.servlet.InjectionState;
import io.opentelemetry.javaagent.bootstrap.servlet.SnippetInjectingResponseWrapper;
import io.opentelemetry.javaagent.instrumentation.servlet.common.snippet.SnippetInjectingPrintWriter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.ServletResponseWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Notes on Content-Length: the snippet length is only added to the content length when injection
 * occurs and the content length was set previously.
 *
 * <p>If the Content-Length is set after snippet injection occurs (either for the first time or is
 * set again for some reason), we intentionally do not add the snippet length, because the
 * application server may be making that call at the end of a request when it sees the request has
 * not been submitted, in which case it is likely using the real length of content that has been
 * written, including the snippet length.
 */
public class Servlet5SnippetInjectingResponseWrapper extends HttpServletResponseWrapper
    implements SnippetInjectingResponseWrapper {

  private static final Logger logger =
      Logger.getLogger(Servlet5SnippetInjectingResponseWrapper.class.getName());

  public static final String FAKE_SNIPPET_HEADER = "FAKE_SNIPPET_HEADER";

  private static final String STATE_ATTRIBUTE =
      Servlet5SnippetInjectingResponseWrapper.class.getName() + ".state";
  private final String snippet;
  private final int snippetLength;

  private final InjectionState injectionState;

  @Nullable private SnippetInjectingPrintWriter snippetInjectingPrintWriter = null;

  public Servlet5SnippetInjectingResponseWrapper(HttpServletResponse response, String snippet) {
    this(null, response, snippet);
  }

  public Servlet5SnippetInjectingResponseWrapper(
      @Nullable HttpServletRequest request, HttpServletResponse response, String snippet) {
    super(response);
    this.snippet = snippet;
    snippetLength = snippet.length();
    Object previous = request == null ? null : request.getAttribute(STATE_ATTRIBUTE);
    if (previous instanceof InjectionState
        && request != null
        && request.getDispatcherType() != DispatcherType.ERROR
        && ((InjectionState) previous).getWrapper()
            instanceof Servlet5SnippetInjectingResponseWrapper
        && unwrapResponse(
                ((Servlet5SnippetInjectingResponseWrapper) ((InjectionState) previous).getWrapper())
                    .getResponse())
            == unwrapResponse(response)) {
      injectionState = (InjectionState) previous;
      injectionState.setWrapper(this);
    } else {
      // Request attributes survive async dispatch and are cleared when the container recycles it.
      injectionState =
          new InjectionState(
              this,
              () ->
                  request == null || request.getAttribute(STATE_ATTRIBUTE) == getInjectionState());
      if (request != null) {
        request.setAttribute(STATE_ATTRIBUTE, injectionState);
      }
    }
  }

  private static ServletResponse unwrapResponse(ServletResponse response) {
    while (response instanceof ServletResponseWrapper) {
      response = ((ServletResponseWrapper) response).getResponse();
    }
    return response;
  }

  public InjectionState getInjectionState() {
    return injectionState;
  }

  @Override
  public void resetBuffer() {
    super.resetBuffer();
    injectionState.resetBuffer();
  }

  @Override
  public void reset() {
    super.reset();
    injectionState.reset();
    snippetInjectingPrintWriter = null;
  }

  @Override
  public boolean containsHeader(String name) {
    // this function is overridden in order to make sure the response is wrapped
    // but not wrapped twice
    // we don't use req.setAttribute
    // because async requests pass down their attributes, but don't pass down our wrapped response
    // and so we would see the presence of the attribute and think the response was already wrapped
    // when it really is not
    // see also https://docs.oracle.com/javaee/7/api/javax/servlet/AsyncContext.html
    if (name.equals(FAKE_SNIPPET_HEADER)) {
      return true;
    }
    return super.containsHeader(name);
  }

  @Override
  public void setHeader(String name, String value) {
    handleHeader(name, value);
    super.setHeader(name, value);
  }

  @Override
  public void addHeader(String name, String value) {
    handleHeader(name, value);
    super.addHeader(name, value);
  }

  private void handleHeader(String name, String value) {
    if ("Content-Length".equalsIgnoreCase(name)) {
      try {
        injectionState.setContentLength(Long.parseLong(value));
      } catch (NumberFormatException e) {
        logger.log(FINE, "Failed to parse the Content-Length header", e);
      }
    }
  }

  @Override
  public void setIntHeader(String name, int value) {
    if ("Content-Length".equalsIgnoreCase(name)) {
      injectionState.setContentLength(value);
    }
    super.setIntHeader(name, value);
  }

  @Override
  public void addIntHeader(String name, int value) {
    if ("Content-Length".equalsIgnoreCase(name)) {
      injectionState.setContentLength(value);
    }
    super.addIntHeader(name, value);
  }

  @Override
  public void setContentLength(int len) {
    injectionState.setContentLength(len);
    super.setContentLength(len);
  }

  @Override
  public void setContentLengthLong(long length) {
    injectionState.setContentLength(length);
    super.setContentLengthLong(length);
  }

  @Override
  public ServletOutputStream getOutputStream() throws IOException {
    ServletOutputStream output = super.getOutputStream();
    initializeInjectionStateIfNeeded(output, this);
    return output;
  }

  @Override
  public PrintWriter getWriter() throws IOException {
    if (snippetInjectingPrintWriter == null) {
      snippetInjectingPrintWriter =
          new SnippetInjectingPrintWriter(super.getWriter(), snippet, injectionState);
    }
    return snippetInjectingPrintWriter;
  }

  @Override
  public boolean isContentTypeTextHtml() {
    String contentType = super.getContentType();
    if (contentType == null) {
      contentType = super.getHeader("content-type");
    }
    return contentType != null
        && (contentType.startsWith("text/html") || contentType.startsWith("application/xhtml+xml"));
  }

  @Override
  public void updateContentLengthIfPreviouslySet() {
    if (injectionState.hasContentLength()) {
      setContentLength((int) injectionState.getContentLength() + snippetLength);
    }
  }

  @Override
  public boolean isNotSafeToInject() {
    // if content-length was set and response was already committed (headers sent to the client),
    // then it is not safe to inject because the content-length header cannot be updated to account
    // for the snippet length
    return injectionState.hasContentLength() && isCommitted();
  }
}

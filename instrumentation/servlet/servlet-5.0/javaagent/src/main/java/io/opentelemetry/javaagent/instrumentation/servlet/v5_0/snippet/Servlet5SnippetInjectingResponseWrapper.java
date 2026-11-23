/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.snippet;

import static io.opentelemetry.javaagent.instrumentation.servlet.v5_0.snippet.ServletOutputStreamInjectionState.initializeInjectionStateIfNeeded;

import io.opentelemetry.javaagent.bootstrap.servlet.InjectionState;
import io.opentelemetry.javaagent.bootstrap.servlet.SnippetInjectingResponseWrapper;
import io.opentelemetry.javaagent.instrumentation.servlet.common.response.JettyContentLength;
import io.opentelemetry.javaagent.instrumentation.servlet.common.response.TomcatContentLength;
import io.opentelemetry.javaagent.instrumentation.servlet.common.snippet.SnippetInjectingPrintWriter;
import io.opentelemetry.javaagent.instrumentation.servlet.v5_0.Servlet5ResponseInjectionState;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.ServletResponseWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.io.PrintWriter;
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

  public static final String FAKE_SNIPPET_HEADER = "FAKE_SNIPPET_HEADER";

  private static final String STATE_ATTRIBUTE =
      Servlet5SnippetInjectingResponseWrapper.class.getName() + ".state";
  private final String snippet;
  private final int snippetLength;

  @Nullable private final HttpServletRequest request;
  private final InjectionState injectionState;

  @Nullable private SnippetInjectingPrintWriter snippetInjectingPrintWriter = null;
  @Nullable private PrintWriter delegatePrintWriter;

  public Servlet5SnippetInjectingResponseWrapper(HttpServletResponse response, String snippet) {
    this(null, response, snippet);
  }

  public Servlet5SnippetInjectingResponseWrapper(
      @Nullable HttpServletRequest request, HttpServletResponse response, String snippet) {
    super(response);
    this.request = request;
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
      if (request.getDispatcherType() != DispatcherType.INCLUDE) {
        injectionState.setContentLengthWrapper(this);
      }
    } else {
      // Request attributes survive async dispatch and are cleared when the container recycles it.
      injectionState = new InjectionState(this);
      if (request != null) {
        request.setAttribute(STATE_ATTRIBUTE, injectionState);
      }
    }
    Servlet5ResponseInjectionState.set(response, injectionState);
  }

  public static void beginForward(HttpServletRequest request) {
    Object state = request.getAttribute(STATE_ATTRIBUTE);
    if (state instanceof InjectionState) {
      ((InjectionState) state).beginForward();
    }
  }

  public static void resetForForward(HttpServletRequest request) {
    Object state = request.getAttribute(STATE_ATTRIBUTE);
    if (state instanceof InjectionState) {
      ((InjectionState) state).resetForForward();
    }
  }

  public static void finishForward(HttpServletRequest request, boolean successful) {
    Object state = request.getAttribute(STATE_ATTRIBUTE);
    if (state instanceof InjectionState) {
      ((InjectionState) state).finishForward(successful);
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
  public void reset() {
    long resetSequence = injectionState.getResetSequence();
    super.reset();
    boolean reset = injectionState.getResetSequence() != resetSequence;
    if (reset) {
      snippetInjectingPrintWriter = null;
      delegatePrintWriter = null;
    }
  }

  @Override
  public boolean containsHeader(String name) {
    // this function is overridden in order to make sure the response is wrapped
    // but not wrapped twice
    // The request attribute tracks body state, not whether this response is wrapped:
    // async dispatch preserves attributes but may pass down the original response.
    // see also https://docs.oracle.com/javaee/7/api/javax/servlet/AsyncContext.html
    if (name.equals(FAKE_SNIPPET_HEADER)) {
      return true;
    }
    return super.containsHeader(name);
  }

  @Override
  public void setHeader(String name, String value) {
    long previousLength = isContentLengthHeader(name) ? getContentLengthHeader() : Long.MIN_VALUE;
    super.setHeader(name, value);
    reconcileContentLengthHeader(
        name,
        previousLength,
        parseContentLength(name, value),
        JettyContentLength.Setter.SET_HEADER);
  }

  @Override
  public void addHeader(String name, String value) {
    long previousLength = isContentLengthHeader(name) ? getContentLengthHeader() : Long.MIN_VALUE;
    super.addHeader(name, value);
    reconcileContentLengthHeader(
        name,
        previousLength,
        parseContentLength(name, value),
        JettyContentLength.Setter.ADD_HEADER);
  }

  private void reconcileContentLengthHeader(
      String name, long previousLength, long declaredLength, JettyContentLength.Setter setter) {
    if (isContentLengthHeader(name)) {
      reconcileContentLength(
          previousLength, declaredLength, setter, isJettyIncludeContentLengthHeader(name));
    }
  }

  @Override
  public void setIntHeader(String name, int value) {
    long previousLength = isContentLengthHeader(name) ? getContentLengthHeader() : Long.MIN_VALUE;
    super.setIntHeader(name, value);
    reconcileContentLengthHeader(
        name, previousLength, value, JettyContentLength.Setter.SET_INT_HEADER);
  }

  @Override
  public void addIntHeader(String name, int value) {
    long previousLength = isContentLengthHeader(name) ? getContentLengthHeader() : Long.MIN_VALUE;
    super.addIntHeader(name, value);
    reconcileContentLengthHeader(
        name, previousLength, value, JettyContentLength.Setter.ADD_INT_HEADER);
  }

  @Override
  public void setContentLength(int len) {
    long previousLength = getContentLengthHeader();
    super.setContentLength(len);
    reconcileContentLength(previousLength, len, JettyContentLength.Setter.CONTENT_LENGTH, false);
  }

  @Override
  public void setContentLengthLong(long length) {
    long previousLength = getContentLengthHeader();
    super.setContentLengthLong(length);
    reconcileContentLength(previousLength, length, JettyContentLength.Setter.CONTENT_LENGTH, false);
  }

  @Override
  public ServletOutputStream getOutputStream() throws IOException {
    ServletOutputStream output = super.getOutputStream();
    initializeInjectionStateIfNeeded(output, injectionState);
    return output;
  }

  @Override
  public PrintWriter getWriter() throws IOException {
    PrintWriter writer = super.getWriter();
    if (snippetInjectingPrintWriter == null || writer != delegatePrintWriter) {
      snippetInjectingPrintWriter =
          new SnippetInjectingPrintWriter(writer, snippet, injectionState);
      delegatePrintWriter = writer;
    }
    injectionState.useWriter();
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
  public boolean isCurrentRequest() {
    return request == null || request.getAttribute(STATE_ATTRIBUTE) == injectionState;
  }

  @Override
  public boolean updateContentLengthIfPreviouslySet() {
    reconcileRawJettyContentLength();
    if (!injectionState.hasContentLength() || injectionState.isContentLengthAdjusted()) {
      return true;
    }
    long length = injectionState.getContentLength() + snippetLength;
    if (length < injectionState.getContentLength() || !setContentLengthAndVerify(length)) {
      return false;
    }
    injectionState.setContentLength(length);
    injectionState.markContentLengthAdjusted();
    return true;
  }

  @Override
  public void resetContentLengthIfPreviouslyAdjusted() {
    reconcileRawJettyContentLength();
    Object contentLengthResponse = injectionState.getContentLengthResponse();
    if (contentLengthResponse != null) {
      TomcatContentLength.record(contentLengthResponse, injectionState);
    }
    if (injectionState.isContentLengthAdjusted()) {
      long length = injectionState.getContentLength() - snippetLength;
      if (setContentLengthAndVerify(length)) {
        injectionState.setContentLength(length);
      }
    }
  }

  private boolean setContentLengthAndVerify(long length) {
    Object contentLengthResponse = injectionState.getContentLengthResponse();
    if (contentLengthResponse != null
        && TomcatContentLength.isTomcatIncludeResponse(super.getResponse())) {
      return TomcatContentLength.set(contentLengthResponse, length);
    }
    if (length <= Integer.MAX_VALUE && length >= 0) {
      super.setContentLength((int) length);
    } else {
      super.setContentLengthLong(length);
    }
    if (getContentLengthHeader() == length
        || (contentLengthResponse != null
            && TomcatContentLength.get(contentLengthResponse) == length)
        || (injectionState.hasContentLength() && injectionState.getContentLength() == length)) {
      return true;
    }
    if (isJettyIncludeResponse()) {
      super.setHeader("org.eclipse.jetty.server.include.Content-Length", String.valueOf(length));
      return getContentLengthHeader() == length
          || (injectionState.hasContentLength() && injectionState.getContentLength() == length);
    }
    return false;
  }

  private void reconcileContentLength(
      long previousLength,
      long declaredLength,
      JettyContentLength.Setter setter,
      boolean includePrefixedHeader) {
    long contentLength = getContentLengthHeader();
    if (contentLength != Long.MIN_VALUE) {
      if (contentLength > 0
          && contentLength == declaredLength
          && injectionState.isContentLengthAdjusted()
          && injectionState.getContentLength() == contentLength
          && JettyContentLength.isContentLengthUpdateAllowed(
              super.getResponse(), setter, includePrefixedHeader)) {
        injectionState.setContentLength(contentLength);
      } else {
        recordContentLength(contentLength);
      }
    } else if (previousLength != Long.MIN_VALUE) {
      injectionState.setContentLength(-1);
    }
  }

  private void reconcileRawJettyContentLength() {
    ServletResponse response = super.getResponse();
    // startAsync() can expose the raw response, bypassing this wrapper's setters.
    // Do not bypass application wrappers or rely on Tomcat's incomplete header view.
    String responseClassName = response.getClass().getName();
    if (responseClassName.equals("org.eclipse.jetty.ee9.nested.Response")
        || responseClassName.equals("org.eclipse.jetty.server.Response")
        || responseClassName.equals("org.eclipse.jetty.ee10.servlet.ServletApiResponse")) {
      long length = getContentLengthHeader();
      if (length != Long.MIN_VALUE) {
        recordContentLength(length);
      } else if (injectionState.hasContentLength()) {
        injectionState.setContentLength(-1);
      }
    }
  }

  private void recordContentLength(long length) {
    if (!injectionState.hasContentLength() || injectionState.getContentLength() != length) {
      injectionState.setContentLength(length);
    }
  }

  private long getContentLengthHeader() {
    String value = super.getHeader("Content-Length");
    if (value == null) {
      return Long.MIN_VALUE;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException ignored) {
      return Long.MIN_VALUE;
    }
  }

  private static boolean isContentLengthHeader(String name) {
    return "Content-Length".equalsIgnoreCase(name)
        || "org.eclipse.jetty.server.include.Content-Length".equalsIgnoreCase(name);
  }

  private static long parseContentLength(String name, String value) {
    String contentLengthHeader = "Content-Length";
    String includePrefix = "org.eclipse.jetty.server.include.";
    if (name.startsWith(includePrefix)) {
      name = name.substring(includePrefix.length());
    }
    if (!contentLengthHeader.equalsIgnoreCase(name) || value == null) {
      return Long.MIN_VALUE;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException ignored) {
      return Long.MIN_VALUE;
    }
  }

  private static boolean isJettyIncludeContentLengthHeader(String name) {
    return name.startsWith("org.eclipse.jetty.server.include.")
        && "Content-Length"
            .equalsIgnoreCase(name.substring("org.eclipse.jetty.server.include.".length()));
  }

  @Override
  public boolean isIncludeRequest() {
    return request != null && request.getDispatcherType() == DispatcherType.INCLUDE;
  }

  private boolean isJettyIncludeResponse() {
    return isJettyResponse()
        && (isIncludeRequest() || injectionState.getWrapper().isIncludeRequest());
  }

  private boolean isJettyResponse() {
    ServletResponse response = super.getResponse();
    while (response instanceof ServletResponseWrapper) {
      response = ((ServletResponseWrapper) response).getResponse();
    }
    String responseClassName = response.getClass().getName();
    return responseClassName.equals("org.eclipse.jetty.ee9.nested.Response")
        || responseClassName.equals("org.eclipse.jetty.server.Response");
  }

  @Override
  public boolean isNotSafeToInject() {
    // if content-length was set and response was already committed (headers sent to the client),
    // then it is not safe to inject because the content-length header cannot be updated to account
    // for the snippet length
    reconcileRawJettyContentLength();
    return injectionState.hasContentLength() && isCommitted();
  }
}

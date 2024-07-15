/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsp;

import io.opentelemetry.sdk.trace.data.SpanData;

class JspSpanAssertionBuilder {
  private SpanData parent;
  private String method;
  private String route;
  private String className;
  private String requestURLOverride;
  private String forwardOrigin;
  private int responseStatus;
  private Class<?> exceptionClass;
  private boolean errorMessageOptional;

  public JspSpanAssertionBuilder withParent(SpanData parent) {
    this.parent = parent;
    return this;
  }

  public JspSpanAssertionBuilder withMethod(String method) {
    this.method = method;
    return this;
  }

  public JspSpanAssertionBuilder withRoute(String route) {
    this.route = route;
    return this;
  }

  public JspSpanAssertionBuilder withClassName(String className) {
    this.className = className;
    return this;
  }

  public JspSpanAssertionBuilder withRequestURLOverride(String requestURLOverride) {
    this.requestURLOverride = requestURLOverride;
    return this;
  }

  public JspSpanAssertionBuilder withForwardOrigin(String forwardOrigin) {
    this.forwardOrigin = forwardOrigin;
    return this;
  }

  public JspSpanAssertionBuilder withResponseStatus(int responseStatus) {
    this.responseStatus = responseStatus;
    return this;
  }

  public JspSpanAssertionBuilder withExceptionClass(Class<?> exceptionClass) {
    this.exceptionClass = exceptionClass;
    return this;
  }

  public JspSpanAssertionBuilder withErrorMessageOptional(boolean errorMessageOptional) {
    this.errorMessageOptional = errorMessageOptional;
    return this;
  }

  public JspSpan build() {
    JspSpan serverSpan = new JspSpan();
    serverSpan.setParent(this.parent);
    serverSpan.setMethod(this.method);
    serverSpan.setRoute(this.route);
    serverSpan.setClassName(this.className);
    serverSpan.setRequestURLOverride(this.requestURLOverride);
    serverSpan.setForwardOrigin(this.forwardOrigin);
    serverSpan.setResponseStatus(this.responseStatus);
    serverSpan.setExceptionClass(this.exceptionClass);
    serverSpan.setErrorMessageOptional(this.errorMessageOptional);
    return serverSpan;
  }
}

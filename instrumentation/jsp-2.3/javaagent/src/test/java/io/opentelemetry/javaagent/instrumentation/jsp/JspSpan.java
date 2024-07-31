/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsp;

import io.opentelemetry.sdk.trace.data.SpanData;

class JspSpan {
  private SpanData parent;
  private String method;
  private String className;
  private String requestUrlOverride;
  private String forwardOrigin;
  private String route;
  private int responseStatus;
  private Class<?> exceptionClass;
  private boolean errorMessageOptional;

  public SpanData getParent() {
    return parent;
  }

  public void setParent(SpanData parent) {
    this.parent = parent;
  }

  public String getMethod() {
    return method;
  }

  public void setMethod(String method) {
    this.method = method;
  }

  public String getClassName() {
    return className;
  }

  public void setClassName(String className) {
    this.className = className;
  }

  public String getRequestUrlOverride() {
    return requestUrlOverride;
  }

  public void setRequestUrlOverride(String requestUrlOverride) {
    this.requestUrlOverride = requestUrlOverride;
  }

  public String getForwardOrigin() {
    return forwardOrigin;
  }

  public void setForwardOrigin(String forwardOrigin) {
    this.forwardOrigin = forwardOrigin;
  }

  public String getRoute() {
    return route;
  }

  public void setRoute(String route) {
    this.route = route;
  }

  public int getResponseStatus() {
    return responseStatus;
  }

  public void setResponseStatus(int responseStatus) {
    this.responseStatus = responseStatus;
  }

  public Class<?> getExceptionClass() {
    return exceptionClass;
  }

  public void setExceptionClass(Class<?> exceptionClass) {
    this.exceptionClass = exceptionClass;
  }

  public boolean getErrorMessageOptional() {
    return errorMessageOptional;
  }

  public void setErrorMessageOptional(boolean errorMessageOptional) {
    this.errorMessageOptional = errorMessageOptional;
  }
}

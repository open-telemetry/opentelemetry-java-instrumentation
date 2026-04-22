/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsp.v2_3;

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

  SpanData getParent() {
    return parent;
  }

  void setParent(SpanData parent) {
    this.parent = parent;
  }

  String getMethod() {
    return method;
  }

  void setMethod(String method) {
    this.method = method;
  }

  String getClassName() {
    return className;
  }

  void setClassName(String className) {
    this.className = className;
  }

  String getRequestUrlOverride() {
    return requestUrlOverride;
  }

  void setRequestUrlOverride(String requestUrlOverride) {
    this.requestUrlOverride = requestUrlOverride;
  }

  String getForwardOrigin() {
    return forwardOrigin;
  }

  void setForwardOrigin(String forwardOrigin) {
    this.forwardOrigin = forwardOrigin;
  }

  String getRoute() {
    return route;
  }

  void setRoute(String route) {
    this.route = route;
  }

  int getResponseStatus() {
    return responseStatus;
  }

  void setResponseStatus(int responseStatus) {
    this.responseStatus = responseStatus;
  }

  Class<?> getExceptionClass() {
    return exceptionClass;
  }

  void setExceptionClass(Class<?> exceptionClass) {
    this.exceptionClass = exceptionClass;
  }

  boolean isErrorMessageOptional() {
    return errorMessageOptional;
  }

  void setErrorMessageOptional(boolean errorMessageOptional) {
    this.errorMessageOptional = errorMessageOptional;
  }
}

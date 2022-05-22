/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.common;

public class JaxWsRequest {
  private final Class<?> codeClass;
  private final String methodName;

  public JaxWsRequest(Class<?> codeClass, String methodName) {
    this.codeClass = codeClass;
    this.methodName = methodName;
  }

  public Class<?> codeClass() {
    return codeClass;
  }

  public String methodName() {
    return methodName;
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.common;

import static io.opentelemetry.javaagent.instrumentation.jaxws.common.JaxWsSingletons.spanNameExtractor;

public class JaxWsRequest {
  private final Class<?> codeClass;
  private final String methodName;
  private final String spanName;

  public JaxWsRequest(Class<?> codeClass, String methodName) {
    this.codeClass = codeClass;
    this.methodName = methodName;
    this.spanName = spanNameExtractor().extract(this);
  }

  public Class<?> codeClass() {
    return codeClass;
  }

  public String methodName() {
    return methodName;
  }

  public String spanName() {
    return spanName;
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ws;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class SpringWsRequest {

  public static SpringWsRequest create(Class<?> codeClass, String methodName) {
    return new AutoValue_SpringWsRequest(codeClass, methodName);
  }

  public abstract Class<?> getCodeClass();

  public abstract String getMethodName();
}

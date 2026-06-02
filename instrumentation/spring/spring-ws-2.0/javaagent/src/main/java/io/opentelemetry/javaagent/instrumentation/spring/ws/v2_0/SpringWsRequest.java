/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ws.v2_0;

import com.google.auto.value.AutoValue;

@AutoValue
abstract class SpringWsRequest {

  static SpringWsRequest create(Class<?> codeClass, String methodName) {
    return new AutoValue_SpringWsRequest(codeClass, methodName);
  }

  abstract Class<?> getCodeClass();

  abstract String getMethodName();
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.instrumentationannotations;

import java.lang.reflect.Method;

class MethodRequest {
  private final Method method;
  private final Object[] args;

  MethodRequest(Method method, Object[] args) {
    this.method = method;
    this.args = args;
  }

  Method method() {
    return this.method;
  }

  Object[] args() {
    return this.args;
  }
}

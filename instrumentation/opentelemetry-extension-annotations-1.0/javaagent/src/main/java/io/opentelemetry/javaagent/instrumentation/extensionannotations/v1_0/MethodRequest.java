/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.extensionannotations.v1_0;

import java.lang.reflect.Method;

public class MethodRequest {
  private final Method method;
  private final Object[] args;

  public MethodRequest(Method method, Object[] args) {
    this.method = method;
    this.args = args;
  }

  public Method method() {
    return this.method;
  }

  public Object[] args() {
    return this.args;
  }
}

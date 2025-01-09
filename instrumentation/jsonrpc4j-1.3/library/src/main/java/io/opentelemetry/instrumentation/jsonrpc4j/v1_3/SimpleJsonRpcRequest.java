/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jsonrpc4j.v1_3;

import java.lang.reflect.Method;

public final class SimpleJsonRpcRequest {

  private final String methodName;
  private final Object argument;
  private Method method;

  public SimpleJsonRpcRequest(String methodName, Object argument) {
    this.methodName = methodName;
    this.argument = argument;
  }

  public SimpleJsonRpcRequest(Method method, Object argument) {
    this.method = method;
    this.methodName = method.getName();
    this.argument = argument;
  }

  public String getMethodName() {
    return methodName;
  }

  public Object getArgument() {
    return argument;
  }

  public Method getMethod() {
    return method;
  }
}

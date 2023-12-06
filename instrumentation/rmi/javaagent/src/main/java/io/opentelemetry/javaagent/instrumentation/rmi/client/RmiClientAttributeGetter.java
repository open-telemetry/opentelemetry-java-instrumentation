/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rmi.client;

import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcAttributeGetter;
import java.lang.reflect.Method;

enum RmiClientAttributeGetter implements RpcAttributeGetter<Method> {
  INSTANCE;

  @Override
  public String getSystem(Method method) {
    return "java_rmi";
  }

  @Override
  public String getService(Method method) {
    return method.getDeclaringClass().getName();
  }

  @Override
  public String getMethod(Method method) {
    return method.getName();
  }
}

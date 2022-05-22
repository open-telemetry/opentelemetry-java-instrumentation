/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rmi.client;

import io.opentelemetry.instrumentation.api.instrumenter.rpc.RpcAttributesGetter;
import java.lang.reflect.Method;

enum RmiClientAttributesGetter implements RpcAttributesGetter<Method> {
  INSTANCE;

  @Override
  public String system(Method method) {
    return "java_rmi";
  }

  @Override
  public String service(Method method) {
    return method.getDeclaringClass().getName();
  }

  @Override
  public String method(Method method) {
    return method.getName();
  }
}

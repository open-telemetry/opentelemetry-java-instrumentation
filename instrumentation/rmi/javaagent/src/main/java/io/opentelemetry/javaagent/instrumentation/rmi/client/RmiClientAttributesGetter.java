/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rmi.client;

import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcAttributesGetter;
import java.lang.reflect.Method;
import javax.annotation.Nullable;

final class RmiClientAttributesGetter implements RpcAttributesGetter<Method, Void> {

  @Override
  public String getSystem(Method method) {
    return "java_rmi";
  }

  @Override
  @Nullable
  public String getService(Method method) {
    return null;
  }

  @Deprecated
  @Override
  @Nullable
  public String getMethod(Method method) {
    return null;
  }

  @Override
  public String getRpcMethod(Method method) {
    return method.getDeclaringClass().getName() + "/" + method.getName();
  }
}

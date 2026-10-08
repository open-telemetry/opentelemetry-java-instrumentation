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
  @Nullable
  public String getErrorType(Method request, @Nullable Void response, @Nullable Throwable error) {
    return null;
  }

  @Override
  public String getSystem(Method method) {
    return "java_rmi";
  }

  @Override
  public String getService(Method method) {
    return method.getDeclaringClass().getName();
  }

  @Deprecated
  @Override
  public String getMethod(Method method) {
    return method.getName();
  }
}

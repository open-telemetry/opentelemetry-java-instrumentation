/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.gwt;

import io.opentelemetry.instrumentation.api.instrumenter.rpc.RpcServerAttributesGetter;
import java.lang.reflect.Method;

enum GwtRpcAttributesGetter implements RpcServerAttributesGetter<Method> {
  INSTANCE;

  @Override
  public String system(Method method) {
    return "gwt";
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

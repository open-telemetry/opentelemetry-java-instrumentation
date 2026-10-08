/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.gwt.v2_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcAttributesGetter;
import java.lang.reflect.Method;
import javax.annotation.Nullable;

class GwtRpcAttributesGetter implements RpcAttributesGetter<Method, Void> {

  @Override
  @Nullable
  public String getErrorType(Method request, @Nullable Void response, @Nullable Throwable error) {
    return null;
  }

  @Override
  public String getSystem(Method method) {
    return "gwt";
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

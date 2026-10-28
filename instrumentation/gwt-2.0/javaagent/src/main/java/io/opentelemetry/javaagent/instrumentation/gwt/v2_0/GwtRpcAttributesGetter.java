/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.gwt.v2_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcAttributesGetter;
import java.lang.reflect.Method;

class GwtRpcAttributesGetter implements RpcAttributesGetter<Method, Void> {

  @Override
  public String getRpcSystemName(Method method) {
    return "gwt";
  }

  @Override
  public String getRpcMethod(Method method) {
    return method.getDeclaringClass().getName() + "/" + method.getName();
  }
}

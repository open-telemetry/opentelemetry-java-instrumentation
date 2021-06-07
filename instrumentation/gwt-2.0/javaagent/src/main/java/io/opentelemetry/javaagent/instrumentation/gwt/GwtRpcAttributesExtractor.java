/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.gwt;

import io.opentelemetry.instrumentation.api.instrumenter.rpc.RpcAttributesExtractor;
import java.lang.reflect.Method;

final class GwtRpcAttributesExtractor extends RpcAttributesExtractor<Method, Void> {
  @Override
  protected String system(Method method) {
    return "gwt";
  }

  @Override
  protected String service(Method method) {
    return method.getDeclaringClass().getName();
  }

  @Override
  protected String method(Method method) {
    return method.getName();
  }
}

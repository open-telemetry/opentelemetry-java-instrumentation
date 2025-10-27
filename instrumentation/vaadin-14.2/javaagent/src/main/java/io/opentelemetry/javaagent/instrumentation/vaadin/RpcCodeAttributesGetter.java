/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vaadin;

import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesGetter;

public class RpcCodeAttributesGetter implements CodeAttributesGetter<VaadinRpcRequest> {

  @Override
  public Class<?> getCodeClass(VaadinRpcRequest request) {
    return request.getRpcInvocationHandler().getClass();
  }

  @Override
  public String getMethodName(VaadinRpcRequest request) {
    return request.getMethodName();
  }
}

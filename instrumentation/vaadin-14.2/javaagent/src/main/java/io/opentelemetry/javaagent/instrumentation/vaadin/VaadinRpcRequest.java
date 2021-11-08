/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vaadin;

import com.google.auto.value.AutoValue;
import com.vaadin.flow.server.communication.rpc.RpcInvocationHandler;
import elemental.json.JsonObject;

@AutoValue
public abstract class VaadinRpcRequest {

  public static VaadinRpcRequest create(
      RpcInvocationHandler rpcInvocationHandler, String methodName, JsonObject jsonObject) {
    return new AutoValue_VaadinRpcRequest(rpcInvocationHandler, methodName, jsonObject);
  }

  abstract RpcInvocationHandler getRpcInvocationHandler();

  abstract String getMethodName();

  abstract JsonObject getJsonObject();
}

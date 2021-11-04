/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vaadin;

import com.google.auto.value.AutoValue;
import com.vaadin.flow.server.communication.rpc.RpcInvocationHandler;
import elemental.json.JsonObject;
import java.lang.reflect.Method;

@AutoValue
public abstract class VaadinRpcRequest {

  public static VaadinRpcRequest create(
      RpcInvocationHandler rpcInvocationHandler, Method method, JsonObject jsonObject) {
    return new AutoValue_VaadinRpcRequest(rpcInvocationHandler, method, jsonObject);
  }

  abstract RpcInvocationHandler getRpcInvocationHandler();

  abstract Method getMethod();

  abstract JsonObject getJsonObject();
}

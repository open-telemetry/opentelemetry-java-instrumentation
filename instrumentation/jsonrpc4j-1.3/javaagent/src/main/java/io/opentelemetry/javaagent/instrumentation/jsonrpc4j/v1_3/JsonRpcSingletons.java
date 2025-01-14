/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsonrpc4j.v1_3;

import com.googlecode.jsonrpc4j.IJsonRpcClient;
import com.googlecode.jsonrpc4j.InvocationListener;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.jsonrpc4j.v1_3.JsonRpcTelemetry;
import io.opentelemetry.instrumentation.jsonrpc4j.v1_3.SimpleJsonRpcRequest;
import io.opentelemetry.instrumentation.jsonrpc4j.v1_3.SimpleJsonRpcResponse;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

public final class JsonRpcSingletons {

  public static final InvocationListener SERVER_INVOCATION_LISTENER;

  public static final Instrumenter<SimpleJsonRpcRequest, SimpleJsonRpcResponse> CLIENT_INSTRUMENTER;

  static {
    JsonRpcTelemetry telemetry = JsonRpcTelemetry.builder(GlobalOpenTelemetry.get()).build();

    SERVER_INVOCATION_LISTENER = telemetry.newServerInvocationListener();
    CLIENT_INSTRUMENTER = telemetry.getClientInstrumenter();
  }

  private JsonRpcSingletons() {}

  @SuppressWarnings({"unchecked"})
  public static <T> T instrumentCreateClientProxy(
      ClassLoader classLoader,
      Class<T> proxyInterface,
      IJsonRpcClient client,
      Map<String, String> extraHeaders,
      Object proxy) {

    return (T)
        Proxy.newProxyInstance(
            classLoader,
            new Class<?>[] {proxyInterface},
            new InvocationHandler() {
              @Override
              public Object invoke(Object proxy1, Method method, Object[] args) throws Throwable {
                // before invoke
                Context parentContext = Context.current();
                SimpleJsonRpcRequest request = new SimpleJsonRpcRequest(method, args);
                if (!CLIENT_INSTRUMENTER.shouldStart(parentContext, request)) {
                  return method.invoke(proxy, args);
                }

                Context context = CLIENT_INSTRUMENTER.start(parentContext, request);
                Scope scope = context.makeCurrent();
                try {
                  Object result = method.invoke(proxy, args);
                  // after invoke
                  scope.close();
                  CLIENT_INSTRUMENTER.end(
                      context,
                      new SimpleJsonRpcRequest(method, args),
                      new SimpleJsonRpcResponse(result),
                      null);
                  return result;

                } catch (Throwable t) {
                  // after invoke
                  scope.close();
                  CLIENT_INSTRUMENTER.end(context, request, null, t);
                  throw t;
                }
              }
            });
  }
}

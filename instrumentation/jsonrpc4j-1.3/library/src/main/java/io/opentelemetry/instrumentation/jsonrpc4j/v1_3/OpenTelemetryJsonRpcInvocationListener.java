/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jsonrpc4j.v1_3;

import com.fasterxml.jackson.databind.JsonNode;
import com.googlecode.jsonrpc4j.InvocationListener;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.lang.reflect.Method;
import java.util.List;

public final class OpenTelemetryJsonRpcInvocationListener implements InvocationListener {

  private final Instrumenter<JsonRpcRequest, JsonRpcResponse> serverInstrumenter;

  private static final ThreadLocal<Context> threadLocalContext = new ThreadLocal<>();
  private static final ThreadLocal<Scope> threadLocalScope = new ThreadLocal<>();

  public OpenTelemetryJsonRpcInvocationListener(
      Instrumenter<JsonRpcRequest, JsonRpcResponse> serverInstrumenter) {
    this.serverInstrumenter = serverInstrumenter;
  }

  /**
   * This method will be invoked prior to a JSON-RPC service being invoked.
   *
   * @param method is the method that will be invoked.
   * @param arguments are the arguments that will be passed to the method when it is invoked.
   */
  @Override
  public void willInvoke(Method method, List<JsonNode> arguments) {
    Context parentContext = Context.current();
    JsonRpcRequest request = new JsonRpcRequest(method, arguments);
    if (!serverInstrumenter.shouldStart(parentContext, request)) {
      return;
    }

    Context context = serverInstrumenter.start(parentContext, request);
    threadLocalContext.set(context);
    threadLocalScope.set(context.makeCurrent());
  }

  /**
   * This method will be invoked after a JSON-RPC service has been invoked.
   *
   * @param method is the method that will was invoked.
   * @param arguments are the arguments that were be passed to the method when it is invoked.
   * @param result is the result of the method invocation. If an error arose, this value will be
   *     null.
   * @param t is the throwable that was thrown from the invocation, if no error arose, this value
   *     will be null.
   * @param duration is approximately the number of milliseconds that elapsed during which the
   *     method was invoked.
   */
  @Override
  public void didInvoke(
      Method method, List<JsonNode> arguments, Object result, Throwable t, long duration) {
    JsonRpcRequest request = new JsonRpcRequest(method, arguments);
    JsonRpcResponse response = new JsonRpcResponse(method, arguments, result);
    threadLocalScope.get().close();
    serverInstrumenter.end(threadLocalContext.get(), request, response, t);
    threadLocalContext.remove();
    threadLocalScope.remove();
  }
}

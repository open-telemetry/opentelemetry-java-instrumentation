/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vaadin;

import com.vaadin.flow.server.communication.rpc.RpcInvocationHandler;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.semconv.util.SpanNames;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;

public class VaadinSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.vaadin-14.2";

  static final ContextKey<VaadinServiceContext> SERVICE_CONTEXT_KEY =
      ContextKey.named("opentelemetry-vaadin-service-context-key");
  static final ContextKey<Object> REQUEST_HANDLER_CONTEXT_KEY =
      ContextKey.named("opentelemetry-vaadin-request-handler-context-key");

  private static final Instrumenter<VaadinClientCallableRequest, Void> CLIENT_CALLABLE_INSTRUMENTER;
  private static final Instrumenter<VaadinHandlerRequest, Void> REQUEST_HANDLER_INSTRUMENTER;
  private static final Instrumenter<VaadinRpcRequest, Void> RPC_INSTRUMENTER;
  private static final Instrumenter<VaadinServiceRequest, Void> SERVICE_INSTRUMENTER;
  private static final VaadinHelper HELPER;

  static {
    ClientCallableCodeAttributesGetter clientCallableAttributesGetter =
        new ClientCallableCodeAttributesGetter();
    CLIENT_CALLABLE_INSTRUMENTER =
        Instrumenter.<VaadinClientCallableRequest, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                CodeSpanNameExtractor.create(clientCallableAttributesGetter))
            .setEnabled(ExperimentalConfig.get().controllerTelemetryEnabled())
            .addAttributesExtractor(CodeAttributesExtractor.create(clientCallableAttributesGetter))
            .buildInstrumenter();

    REQUEST_HANDLER_INSTRUMENTER =
        Instrumenter.<VaadinHandlerRequest, Void>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, VaadinHandlerRequest::getSpanName)
            .setEnabled(ExperimentalConfig.get().controllerTelemetryEnabled())
            // add context for tracking nested request handler calls
            .addContextCustomizer(
                (context, vaadinHandlerRequest, startAttributes) ->
                    context.with(REQUEST_HANDLER_CONTEXT_KEY, true))
            .buildInstrumenter();

    RPC_INSTRUMENTER =
        Instrumenter.<VaadinRpcRequest, Void>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, VaadinSingletons::rpcSpanName)
            .setEnabled(ExperimentalConfig.get().controllerTelemetryEnabled())
            .buildInstrumenter();

    SERVICE_INSTRUMENTER =
        Instrumenter.<VaadinServiceRequest, Void>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, VaadinServiceRequest::getSpanName)
            .setEnabled(ExperimentalConfig.get().controllerTelemetryEnabled())
            // add context for tracking whether any request handler handled the request
            .addContextCustomizer(
                (context, vaadinServiceRequest, startAttributes) ->
                    context.with(SERVICE_CONTEXT_KEY, new VaadinServiceContext()))
            .buildInstrumenter();

    HELPER = new VaadinHelper(REQUEST_HANDLER_INSTRUMENTER, SERVICE_INSTRUMENTER);
  }

  public static Instrumenter<VaadinClientCallableRequest, Void> clientCallableInstrumenter() {
    return CLIENT_CALLABLE_INSTRUMENTER;
  }

  public static Instrumenter<VaadinRpcRequest, Void> rpcInstrumenter() {
    return RPC_INSTRUMENTER;
  }

  public static VaadinHelper helper() {
    return HELPER;
  }

  private static String rpcSpanName(VaadinRpcRequest rpcRequest) {
    RpcInvocationHandler rpcInvocationHandler = rpcRequest.getRpcInvocationHandler();
    String spanName =
        SpanNames.fromMethod(rpcInvocationHandler.getClass(), rpcRequest.getMethodName());
    if ("event".equals(rpcInvocationHandler.getRpcType())) {
      String eventType = rpcRequest.getJsonObject().getString("event");
      if (eventType != null) {
        // append event type to make span name more descriptive
        spanName += "/" + eventType;
      }
    }
    return spanName;
  }

  private VaadinSingletons() {}
}

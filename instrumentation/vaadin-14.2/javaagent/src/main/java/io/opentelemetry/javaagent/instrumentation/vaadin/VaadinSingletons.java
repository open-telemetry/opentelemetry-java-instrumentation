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

  private static final Instrumenter<VaadinClientCallableRequest, Void> clientCallableInstrumenter;
  private static final Instrumenter<VaadinHandlerRequest, Void> requestHandlerInstrumenter;
  private static final Instrumenter<VaadinRpcRequest, Void> rpcInstrumenter;
  private static final Instrumenter<VaadinServiceRequest, Void> serviceInstrumenter;
  private static final VaadinHelper helper;

  static {
    ClientCallableCodeAttributesGetter clientCallableAttributesGetter =
        new ClientCallableCodeAttributesGetter();
    clientCallableInstrumenter =
        Instrumenter.<VaadinClientCallableRequest, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                CodeSpanNameExtractor.create(clientCallableAttributesGetter))
            .setEnabled(ExperimentalConfig.get().controllerTelemetryEnabled())
            .addAttributesExtractor(CodeAttributesExtractor.create(clientCallableAttributesGetter))
            .buildInstrumenter();

    requestHandlerInstrumenter =
        Instrumenter.<VaadinHandlerRequest, Void>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, VaadinHandlerRequest::getSpanName)
            .setEnabled(ExperimentalConfig.get().controllerTelemetryEnabled())
            // add context for tracking nested request handler calls
            .addContextCustomizer(
                (context, vaadinHandlerRequest, startAttributes) ->
                    context.with(REQUEST_HANDLER_CONTEXT_KEY, true))
            .buildInstrumenter();

    RpcCodeAttributesGetter rpcCodeAttributesGetter = new RpcCodeAttributesGetter();
    rpcInstrumenter =
        Instrumenter.<VaadinRpcRequest, Void>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, VaadinSingletons::rpcSpanName)
            .setEnabled(ExperimentalConfig.get().controllerTelemetryEnabled())
            .addAttributesExtractor(CodeAttributesExtractor.create(rpcCodeAttributesGetter))
            .buildInstrumenter();

    serviceInstrumenter =
        Instrumenter.<VaadinServiceRequest, Void>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, VaadinServiceRequest::getSpanName)
            .setEnabled(ExperimentalConfig.get().controllerTelemetryEnabled())
            // add context for tracking whether any request handler handled the request
            .addContextCustomizer(
                (context, vaadinServiceRequest, startAttributes) ->
                    context.with(SERVICE_CONTEXT_KEY, new VaadinServiceContext()))
            .buildInstrumenter();

    helper = new VaadinHelper(requestHandlerInstrumenter, serviceInstrumenter);
  }

  public static Instrumenter<VaadinClientCallableRequest, Void> clientCallableInstrumenter() {
    return clientCallableInstrumenter;
  }

  public static Instrumenter<VaadinRpcRequest, Void> rpcInstrumenter() {
    return rpcInstrumenter;
  }

  public static VaadinHelper helper() {
    return helper;
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

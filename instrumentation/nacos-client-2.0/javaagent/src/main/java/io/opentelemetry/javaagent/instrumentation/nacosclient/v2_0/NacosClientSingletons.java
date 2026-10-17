/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nacosclient.v2_0;

import com.alibaba.nacos.api.remote.response.Response;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;
import javax.annotation.Nullable;

public class NacosClientSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.nacos-client-2.0";

  private static final Instrumenter<NacosClientRequest, Response> clientInstrumenter =
      createClientInstrumenter();
  private static final Instrumenter<NacosClientRequest, Response> serverInstrumenter =
      createServerInstrumenter();

  private NacosClientSingletons() {}

  public static ContextAndScope startClientSpan(NacosClientRequest request) {
    Context parentContext = Context.current();
    if (!clientInstrumenter.shouldStart(parentContext, request)) {
      return null;
    }
    Context context = clientInstrumenter.start(parentContext, request);
    return new ContextAndScope(context, context.makeCurrent());
  }

  public static void endClientSpan(
      @Nullable ContextAndScope contextAndScope,
      NacosClientRequest request,
      @Nullable Response response,
      @Nullable Throwable error) {
    if (contextAndScope == null) {
      return;
    }
    contextAndScope.closeScope();
    clientInstrumenter.end(
        contextAndScope.getContext(), request, response, selectError(response, error));
  }

  public static ContextAndScope startServerSpan(NacosClientRequest request) {
    Context parentContext = Context.current();
    if (!serverInstrumenter.shouldStart(parentContext, request)) {
      return null;
    }
    Context context = serverInstrumenter.start(parentContext, request);
    return new ContextAndScope(context, context.makeCurrent());
  }

  public static void endServerSpan(
      @Nullable ContextAndScope contextAndScope,
      NacosClientRequest request,
      @Nullable Response response,
      @Nullable Throwable error) {
    if (contextAndScope == null) {
      return;
    }
    contextAndScope.closeScope();
    serverInstrumenter.end(
        contextAndScope.getContext(), request, response, selectError(response, error));
  }

  private static Instrumenter<NacosClientRequest, Response> createClientInstrumenter() {
    NacosClientRpcAttributesGetter rpcAttributesGetter = new NacosClientRpcAttributesGetter();
    NacosClientNetworkAttributesGetter networkAttributesGetter =
        new NacosClientNetworkAttributesGetter();
    InstrumenterBuilder<NacosClientRequest, Response> builder =
        Instrumenter.<NacosClientRequest, Response>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, NacosClientRequest::spanName)
            .addAttributesExtractor(RpcClientAttributesExtractor.create(rpcAttributesGetter))
            .addAttributesExtractor(ServerAttributesExtractor.create(networkAttributesGetter))
            .addAttributesExtractor(NetworkAttributesExtractor.create(networkAttributesGetter))
            .addAttributesExtractor(new NacosClientAttributesExtractor());
    return builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  private static Instrumenter<NacosClientRequest, Response> createServerInstrumenter() {
    NacosClientRpcAttributesGetter rpcAttributesGetter = new NacosClientRpcAttributesGetter();
    NacosClientNetworkAttributesGetter networkAttributesGetter =
        new NacosClientNetworkAttributesGetter();
    InstrumenterBuilder<NacosClientRequest, Response> builder =
        Instrumenter.<NacosClientRequest, Response>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, NacosClientRequest::spanName)
            .addAttributesExtractor(RpcServerAttributesExtractor.create(rpcAttributesGetter))
            .addAttributesExtractor(ServerAttributesExtractor.create(networkAttributesGetter))
            .addAttributesExtractor(NetworkAttributesExtractor.create(networkAttributesGetter))
            .addAttributesExtractor(new NacosClientAttributesExtractor());
    return builder.buildInstrumenter(SpanKindExtractor.alwaysServer());
  }

  @Nullable
  private static Throwable selectError(@Nullable Response response, @Nullable Throwable error) {
    if (error != null) {
      return error;
    }
    if (response != null && !response.isSuccess()) {
      String message = response.getMessage();
      int errorCode = response.getErrorCode();
      return new IllegalStateException(
          "Nacos request failed"
              + (errorCode != 0 ? " with code " + errorCode : "")
              + (message != null && !message.isEmpty() ? ": " + message : ""));
    }
    return null;
  }
}

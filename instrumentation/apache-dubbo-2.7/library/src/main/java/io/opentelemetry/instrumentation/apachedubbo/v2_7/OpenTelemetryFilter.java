/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.rpc.RpcSpanNameExtractor;
import io.opentelemetry.javaagent.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import java.util.concurrent.CompletableFuture;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcInvocation;

@Activate(group = {"consumer", "provider"})
public class OpenTelemetryFilter implements Filter {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.apache-dubbo-2.7";

  private final Instrumenter<DubboRequest, Result> serverInstrumenter;
  private final Instrumenter<DubboRequest, Result> clientInstrumenter;

  public OpenTelemetryFilter() {
    DubboRpcAttributesExtractor rpcAttributesExtractor = new DubboRpcAttributesExtractor();
    DubboNetAttributesExtractor netAttributesExtractor = new DubboNetAttributesExtractor();
    SpanNameExtractor<DubboRequest> spanNameExtractor =
        RpcSpanNameExtractor.create(rpcAttributesExtractor);

    InstrumenterBuilder<DubboRequest, Result> builder =
        Instrumenter.<DubboRequest, Result>newBuilder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor)
            .addAttributesExtractor(rpcAttributesExtractor)
            .addAttributesExtractor(netAttributesExtractor)
            .addAttributesExtractor(PeerServiceAttributesExtractor.create(netAttributesExtractor));

    serverInstrumenter = builder.newServerInstrumenter(new DubboHeadersGetter());
    clientInstrumenter = builder.newClientInstrumenter(new DubboHeadersSetter());
  }

  @Override
  public Result invoke(Invoker<?> invoker, Invocation invocation) {
    if (!(invocation instanceof RpcInvocation)) {
      return invoker.invoke(invocation);
    }

    RpcContext rpcContext = RpcContext.getContext();
    boolean isServer = rpcContext.isProviderSide();
    Instrumenter<DubboRequest, Result> instrumenter =
        isServer ? serverInstrumenter : clientInstrumenter;
    Context parentContext = Context.current();
    DubboRequest request = DubboRequest.create((RpcInvocation) invocation, rpcContext);

    if (!instrumenter.shouldStart(parentContext, request)) {
      return invoker.invoke(invocation);
    }
    Context context = instrumenter.start(parentContext, request);

    final Result result;
    boolean isSynchronous = true;
    try (Scope ignored = context.makeCurrent()) {
      result = invoker.invoke(invocation);
      if (!isServer) {
        CompletableFuture<Object> future = rpcContext.getCompletableFuture();
        if (future != null) {
          isSynchronous = false;
          future.whenComplete(
              (o, throwable) -> instrumenter.end(context, request, result, throwable));
        }
      }
    } catch (Throwable e) {
      instrumenter.end(context, request, null, e);
      throw e;
    }
    if (isSynchronous) {
      instrumenter.end(context, request, result, null);
    }
    return result;
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.SERVER;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.tracer.RpcServerTracer;
import io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcInvocation;

class DubboTracer extends RpcServerTracer<RpcInvocation> {

  protected DubboTracer() {}

  DubboTracer(Tracer tracer) {
    super(tracer);
  }

  public Context startServerSpan(
      String interfaceName, String methodName, RpcInvocation rpcInvocation) {
    SpanBuilder spanBuilder =
        tracer
            .spanBuilder(DubboHelper.getSpanName(interfaceName, methodName))
            .setSpanKind(SERVER)
            .setParent(extract(rpcInvocation, getGetter()));
    spanBuilder.setAttribute(SemanticAttributes.RPC_SYSTEM, "dubbo");
    Span span = spanBuilder.startSpan();
    DubboHelper.prepareSpan(span, interfaceName, methodName);
    NetPeerUtils.INSTANCE.setNetPeer(span, RpcContext.getContext().getRemoteAddress());
    return withServerSpan(Context.current(), span);
  }

  public Context startClientSpan(String interfaceName, String methodName) {
    SpanBuilder spanBuilder =
        tracer.spanBuilder(DubboHelper.getSpanName(interfaceName, methodName)).setSpanKind(CLIENT);
    spanBuilder.setAttribute(SemanticAttributes.RPC_SYSTEM, "dubbo");
    Span span = spanBuilder.startSpan();
    DubboHelper.prepareSpan(span, interfaceName, methodName);
    NetPeerUtils.INSTANCE.setNetPeer(span, RpcContext.getContext().getRemoteAddress());
    return withClientSpan(Context.current(), span);
  }

  public void endSpan(Span span, Result result) {
    span.setStatus(DubboHelper.statusFromResult(result));
    end(span);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.apache-dubbo";
  }

  @Override
  protected TextMapGetter<RpcInvocation> getGetter() {
    return DubboExtractAdapter.GETTER;
  }
}

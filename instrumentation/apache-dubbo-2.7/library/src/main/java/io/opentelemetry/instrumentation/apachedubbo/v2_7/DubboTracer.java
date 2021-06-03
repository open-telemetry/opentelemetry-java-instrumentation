/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.SERVER;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.tracer.RpcServerTracer;
import io.opentelemetry.instrumentation.api.tracer.net.NetPeerAttributes;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcInvocation;

class DubboTracer extends RpcServerTracer<RpcInvocation> {

  protected DubboTracer() {}

  public Context startServerSpan(
      String interfaceName, String methodName, RpcInvocation rpcInvocation) {
    Context parentContext = extract(rpcInvocation, getGetter());
    SpanBuilder spanBuilder =
        spanBuilder(parentContext, DubboHelper.getSpanName(interfaceName, methodName), SERVER)
            .setAttribute(SemanticAttributes.RPC_SYSTEM, "dubbo");
    DubboHelper.prepareSpan(spanBuilder, interfaceName, methodName);
    NetPeerAttributes.INSTANCE.setNetPeer(spanBuilder, RpcContext.getContext().getRemoteAddress());
    return withServerSpan(Context.current(), spanBuilder.startSpan());
  }

  public Context startClientSpan(String interfaceName, String methodName) {
    Context parentContext = Context.current();
    SpanBuilder spanBuilder =
        spanBuilder(parentContext, DubboHelper.getSpanName(interfaceName, methodName), CLIENT)
            .setAttribute(SemanticAttributes.RPC_SYSTEM, "dubbo");
    DubboHelper.prepareSpan(spanBuilder, interfaceName, methodName);
    NetPeerAttributes.INSTANCE.setNetPeer(spanBuilder, RpcContext.getContext().getRemoteAddress());
    return withClientSpan(parentContext, spanBuilder.startSpan());
  }

  public void end(Context context, Result result) {
    StatusCode statusCode = DubboHelper.statusFromResult(result);
    if (statusCode != StatusCode.UNSET) {
      Span.fromContext(context).setStatus(statusCode);
    }
    end(context);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.apache-dubbo-2.7";
  }

  @Override
  protected TextMapGetter<RpcInvocation> getGetter() {
    return DubboExtractAdapter.GETTER;
  }
}

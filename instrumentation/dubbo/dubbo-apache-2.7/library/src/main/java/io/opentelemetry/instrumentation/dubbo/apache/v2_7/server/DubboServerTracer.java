/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.dubbo.apache.v2_7.server;

import static io.opentelemetry.api.trace.Span.Kind.SERVER;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator.Getter;
import io.opentelemetry.instrumentation.api.tracer.RpcServerTracer;
import io.opentelemetry.instrumentation.dubbo.apache.v2_7.common.DubboHelper;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcInvocation;

public class DubboServerTracer extends RpcServerTracer<RpcInvocation> {

  DubboServerTracer() {}

  DubboServerTracer(Tracer tracer) {
    super(tracer);
  }

  public Context startSpan(String interfaceName, String methodName, RpcInvocation rpcInvocation) {
    SpanBuilder spanBuilder =
        tracer
            .spanBuilder(DubboHelper.getSpanName(interfaceName, methodName))
            .setSpanKind(SERVER)
            .setParent(extract(rpcInvocation, getGetter()));
    spanBuilder.setAttribute(SemanticAttributes.RPC_SYSTEM, "dubbo");
    Span span = spanBuilder.startSpan();
    DubboHelper.prepareSpan(span, interfaceName, methodName);
    return withServerSpan(Context.current(), span);
  }

  public void endSpan(Span span, Result result) {
    span.setStatus(DubboHelper.statusFromResult(result));
    end(span);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.dubbo";
  }

  @Override
  protected Getter<RpcInvocation> getGetter() {
    return DubboExtractAdapter.GETTER;
  }
}

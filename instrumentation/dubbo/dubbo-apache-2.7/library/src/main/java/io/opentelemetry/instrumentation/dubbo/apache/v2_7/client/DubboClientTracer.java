/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.dubbo.apache.v2_7.client;

import static io.opentelemetry.api.trace.Span.Kind.CLIENT;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.RpcClientTracer;
import io.opentelemetry.instrumentation.dubbo.apache.v2_7.common.DubboHelper;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.apache.dubbo.rpc.Result;

public class DubboClientTracer extends RpcClientTracer {

  protected DubboClientTracer() {}

  protected DubboClientTracer(Tracer tracer) {
    super(tracer);
  }

  public Context startSpan(String interfaceName, String methodName) {
    SpanBuilder spanBuilder =
        tracer.spanBuilder(DubboHelper.getSpanName(interfaceName, methodName)).setSpanKind(CLIENT);
    spanBuilder.setAttribute(SemanticAttributes.RPC_SYSTEM, "dubbo");
    Span span = spanBuilder.startSpan();
    DubboHelper.prepareSpan(span, interfaceName, methodName);
    return withClientSpan(Context.current(), span);
  }

  public void endSpan(Span span, Result result) {
    span.setStatus(DubboHelper.statusFromResult(result));
    end(span);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.dubbo";
  }
}

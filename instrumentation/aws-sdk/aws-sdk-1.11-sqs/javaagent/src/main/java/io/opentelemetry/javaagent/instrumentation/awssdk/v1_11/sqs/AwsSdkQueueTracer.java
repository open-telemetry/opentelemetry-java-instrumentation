/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11.sqs;

import com.amazonaws.Request;
import com.amazonaws.Response;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.javaagent.instrumentation.awssdk.v1_11.AwsSdkTracerSupport;
import io.opentelemetry.javaagent.instrumentation.awssdk.v1_11.RequestMeta;

public class AwsSdkQueueTracer extends BaseTracer {

  private static final AwsSdkQueueTracer TRACER = new AwsSdkQueueTracer();

  public static AwsSdkQueueTracer tracer() {
    return TRACER;
  }

  private final AwsSdkTracerSupport awsSdkTracerSupport = new AwsSdkTracerSupport();

  public AwsSdkQueueTracer() {}

  private String spanNameForRequest(Request<?> request) {
    return awsSdkTracerSupport.spanNameForRequest(request, "SQS");
  }

  public Context startSpan(
      Span.Kind kind, Context parentContext, Request<?> request, RequestMeta requestMeta) {
    Span span =
        tracer
            .spanBuilder(spanNameForRequest(request))
            .setSpanKind(kind)
            .setParent(parentContext)
            .startSpan();

    Context context = parentContext.with(span);
    awsSdkTracerSupport.onNewSpan(context, request, requestMeta);
    return context;
  }

  public void end(Context context, Response<?> response) {
    Span span = Span.fromContext(context);
    onResponse(span, response);
    super.end(span, -1);
  }

  public void onResponse(Span span, Response<?> response) {
    awsSdkTracerSupport.onResponse(span, response);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.aws-sdk-sqs";
  }
}

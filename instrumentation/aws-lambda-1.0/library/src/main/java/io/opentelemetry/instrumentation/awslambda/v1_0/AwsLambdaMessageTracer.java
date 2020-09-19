/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import io.grpc.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator.Getter;
import io.opentelemetry.extensions.trace.propagation.AwsXRayPropagator;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Span.Kind;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.Tracer;
import io.opentelemetry.trace.TracingContextUtils;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import java.util.Collections;
import java.util.Map;

public class AwsLambdaMessageTracer extends BaseTracer {

  private static final String AWS_TRACE_HEADER_PROPAGATOR_KEY = "X-Amzn-Trace-Id";
  private static final String AWS_TRACE_HEADER_SQS_ATTRIBUTE_KEY = "AWSTraceHeader";

  public AwsLambdaMessageTracer() {}

  public AwsLambdaMessageTracer(Tracer tracer) {
    super(tracer);
  }

  public Span startSpan(com.amazonaws.services.lambda.runtime.Context context, SQSEvent event) {
    // Use event source in name if all messages have the same source, otherwise use placeholder.
    String source = "multiple_sources";
    if (!event.getRecords().isEmpty()) {
      String messageSource = event.getRecords().get(0).getEventSource();
      for (int i = 1; i < event.getRecords().size(); i++) {
        SQSMessage message = event.getRecords().get(i);
        if (!message.getEventSource().equals(messageSource)) {
          messageSource = null;
          break;
        }
      }
      if (messageSource != null) {
        source = messageSource;
      }
    }

    Span.Builder span = tracer.spanBuilder(source + " process").setSpanKind(Kind.CONSUMER);

    SemanticAttributes.MESSAGING_SYSTEM.set(span, "AmazonSQS");
    SemanticAttributes.MESSAGING_OPERATION.set(span, "process");

    for (SQSMessage message : event.getRecords()) {
      addLinkToMessageParent(message, span);
    }

    return span.startSpan();
  }

  public Span startSpan(SQSMessage message) {
    Span.Builder span =
        tracer.spanBuilder(message.getEventSource() + " process").setSpanKind(Kind.CONSUMER);

    SemanticAttributes.MESSAGING_SYSTEM.set(span, "AmazonSQS");
    SemanticAttributes.MESSAGING_OPERATION.set(span, "process");
    SemanticAttributes.MESSAGING_MESSAGE_ID.set(span, message.getMessageId());
    SemanticAttributes.MESSAGING_DESTINATION.set(span, message.getEventSource());

    addLinkToMessageParent(message, span);

    return span.startSpan();
  }

  public Scope startScope(Span span) {
    return TracingContextUtils.currentContextWith(span);
  }

  private void addLinkToMessageParent(SQSMessage message, Span.Builder span) {
    String parentHeader = message.getAttributes().get(AWS_TRACE_HEADER_SQS_ATTRIBUTE_KEY);
    if (parentHeader != null) {
      SpanContext parentCtx = TracingContextUtils.getSpan(extractParent(parentHeader)).getContext();
      if (parentCtx.isValid()) {
        span.addLink(parentCtx);
      }
    }
  }

  private static Context extractParent(String parentHeader) {
    return AwsXRayPropagator.getInstance()
        .extract(
            Context.current(),
            Collections.singletonMap(AWS_TRACE_HEADER_PROPAGATOR_KEY, parentHeader),
            MapGetter.INSTANCE);
  }

  private static class MapGetter implements Getter<Map<String, String>> {

    private static final MapGetter INSTANCE = new MapGetter();

    @Override
    public String get(Map<String, String> map, String s) {
      return map.get(s);
    }
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.aws-lambda-1.0";
  }
}

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
import com.amazonaws.services.lambda.runtime.events.SQSEvent.MessageAttribute;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import io.grpc.Context;
import io.opentelemetry.context.propagation.TextMapPropagator.Getter;
import io.opentelemetry.extensions.trace.propagation.AwsXRayPropagator;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Span.Kind;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.Tracer;
import io.opentelemetry.trace.TracingContextUtils;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import java.util.Collections;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.Nullable;

public class AwsLambdaMessageTracer extends AwsLambdaTracer {

  private static final String AWS_TRACE_HEADER_PROPAGATOR_KEY = "X-Amzn-Trace-Id";
  private static final String AWS_TRACE_HEADER_LAMBDA_ENVIRONMENT_KEY = "_X_AMZN_TRACE_ID";
  private static final String AWS_TRACE_HEADER_SQS_ATTRIBUTE_KEY = "AWSTraceHeader";

  public AwsLambdaMessageTracer() {}

  public AwsLambdaMessageTracer(Tracer tracer) {
    super(tracer);
  }

  public Span startSpan(com.amazonaws.services.lambda.runtime.Context context, SQSEvent event) {
    Span.Builder span = createSpan(context).setSpanKind(Kind.CONSUMER);

    SemanticAttributes.MESSAGING_SYSTEM.set(span, "AmazonSQS");
    SemanticAttributes.MESSAGING_OPERATION.set(span, "receive");

    findParent(event, span);

    return span.startSpan();
  }

  public Span startSpan(SQSMessage message) {
    Span.Builder span = tracer.spanBuilder(message.getEventSource() + " process").setSpanKind(Kind.CONSUMER);

    SemanticAttributes.MESSAGING_SYSTEM.set(span, "AmazonSQS");
    SemanticAttributes.MESSAGING_OPERATION.set(span, "process");

    String parentHeader = getMessageParentHeader(message);
    if (parentHeader != null) {
      // If message has a parent, connect them and add a link instead to the implicit context parent
      // which should be a receive span.
      setParent(span, parentHeader);
      SpanContext implicitParent = TracingContextUtils.getCurrentSpan().getContext();
      if (implicitParent.isValid()) {
        span.addLink(implicitParent);
      }
    }

    return span.startSpan();
  }

  private void setParent(Span.Builder span, String parentHeader) {
    Context parent =
        AwsXRayPropagator.getInstance()
            .extract(
                Context.current(),
                Collections.singletonMap(AWS_TRACE_HEADER_PROPAGATOR_KEY, parentHeader),
                MapGetter.INSTANCE);
    span.setParent(parent);
  }

  private void findParent(SQSEvent event, Span.Builder span) {
    boolean foundParent = false;

    // If every message has the same parent, we can link our new span corresponding to the batch of
    // messages to that parent. In practice, this will generally happen when there is one message in
    // the batch and we can provide a "normal trace".

    // Should never be empty but just in case.
    if (!event.getRecords().isEmpty()) {
      String parentHeader = getMessageParentHeader(event.getRecords().get(0));
      if (parentHeader != null) {
        boolean allMessagesHaveSameHeader = true;

        for (int i = 1; i < event.getRecords().size(); i++) {
          String messageParentHeader = getMessageParentHeader(event.getRecords().get(i));
          if (messageParentHeader == null || !messageParentHeader.equals(parentHeader)) {
            allMessagesHaveSameHeader = false;
            break;
          }
        }

        if (allMessagesHaveSameHeader) {
          setParent(span, parentHeader);
          foundParent = true;
        }
      }
    }

    String lambdaParentHeader = System.getenv(AWS_TRACE_HEADER_LAMBDA_ENVIRONMENT_KEY);
    if (lambdaParentHeader != null) {
      Context lambdaParentCtx =
          AwsXRayPropagator.getInstance()
              .extract(
                  Context.current(),
                  Collections.singletonMap(
                      AWS_TRACE_HEADER_LAMBDA_ENVIRONMENT_KEY, lambdaParentHeader),
                  MapGetter.INSTANCE);
      SpanContext lambdaParentSpanCtx = TracingContextUtils.getSpan(lambdaParentCtx).getContext();
      if (lambdaParentSpanCtx.isValid()) {
        if (foundParent) {
          // Span is connected to a trace, add a link to the lambda parent.
          span.addLink(lambdaParentSpanCtx);
        } else {
          // Span is not connected to a trace, more information if we connect directly to the lambda
          // parent instead of treating it as a root with a link.
          span.setParent(lambdaParentCtx);
        }
      }
    }
  }

  @Nullable
  private static String getMessageParentHeader(SQSMessage message) {
    MessageAttribute parentAttribute =
        message.getMessageAttributes().get(AWS_TRACE_HEADER_SQS_ATTRIBUTE_KEY);
    return parentAttribute != null ? parentAttribute.getStringValue() : null;
  }

  private static class MapGetter implements Getter<Map<String, String>> {

    private static final MapGetter INSTANCE = new MapGetter();

    @Override
    public String get(Map<String, String> map, String s) {
      return map.get(s);
    }
  }
}

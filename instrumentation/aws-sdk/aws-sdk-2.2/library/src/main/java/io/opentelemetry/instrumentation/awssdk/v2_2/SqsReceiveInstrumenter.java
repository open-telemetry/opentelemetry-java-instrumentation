package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.UnsafeAttributes;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import javax.annotation.Nullable;

import static io.opentelemetry.instrumentation.awssdk.v2_2.TracingExecutionInterceptor.CONTEXT_ATTRIBUTE;

/*
  Span links can only be created at span creation time. Therefore, the instrumentation uses the
  request to generate the span links. But for receive spans, the data to create the span links
  lives in the response.

  This class gets around this by overriding the start and end function calls in order to create
  the span from the end function call.
 */
public class SqsReceiveInstrumenter extends Instrumenter<ExecutionAttributes, SdkHttpResponse> {
  public SqsReceiveInstrumenter(InstrumenterBuilder<ExecutionAttributes, SdkHttpResponse> builder) {
    super(builder);
  }

  /*
    We cannot start the span context here because we do not have the span links.
    And span links must be added at span creation time.
   */
  @Override
  public Context start(Context parentContext, ExecutionAttributes request) {
    return null;
  }

  /*
    We cannot end the span here because we do not have access to the TracingExecutionInterceptor.
   */
  @Override
  public void end(Context context, ExecutionAttributes request, @Nullable SdkHttpResponse response, @Nullable Throwable error) {
  }

  public void startAndEnd(
      software.amazon.awssdk.core.interceptor.Context.AfterExecution context,
      ExecutionAttributes request,
      TracingExecutionInterceptor config) {

    SpanBuilder spanBuilder =
        tracer.spanBuilder(spanNameExtractor.extract(request)).setSpanKind(SpanKind.CONSUMER);

    Context parentContext = request.getAttribute(CONTEXT_ATTRIBUTE);

    if (parentContext != null) {
      spanBuilder.setParent(parentContext);
    }

    spanBuilder.setAttribute(SemanticAttributes.MESSAGING_OPERATION,
        SemanticAttributes.MessagingOperationValues.RECEIVE);

    spanBuilder.setAttribute(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS");

    UnsafeAttributes attributes = new UnsafeAttributes();
    for (AttributesExtractor<? super ExecutionAttributes, ? super SdkHttpResponse> extractor : attributesExtractors) {
      extractor.onStart(attributes, parentContext, request);
    }
    spanBuilder.setAllAttributes(attributes);

    long totalPayloadSizeInBytes = 0;

    ReceiveMessageResponse receiveMessageResponse = (ReceiveMessageResponse) context.response();

    for (Message message: receiveMessageResponse.messages()) {
      SpanContext spanContext  = getParentContext(config, message);

      if (spanContext.isValid()) {
        spanBuilder.addLink(spanContext);
      }

      totalPayloadSizeInBytes += message.body().length();
    }

    spanBuilder.setAttribute(SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES, totalPayloadSizeInBytes);

    if (shouldStart(io.opentelemetry.context.Context.root(), request)) {
      Span consumerSpan = spanBuilder.startSpan();
      consumerSpan.end();
    }
  }

  private static SpanContext getParentContext(
      TracingExecutionInterceptor config, Message message) {
    io.opentelemetry.context.Context parentContext = io.opentelemetry.context.Context.root();

    TextMapPropagator messagingPropagator = config.getMessagingPropagator();
    if (messagingPropagator != null) {
      parentContext =
          SqsParentContext.ofMessageAttributes(message.messageAttributes(), messagingPropagator);
    }

    if (config.shouldUseXrayPropagator()
        && parentContext == io.opentelemetry.context.Context.root()) {
      parentContext = SqsParentContext.ofSystemAttributes(message.attributesAsStrings());
    }

    return Span.fromContext(parentContext).getSpanContext();
  }
}

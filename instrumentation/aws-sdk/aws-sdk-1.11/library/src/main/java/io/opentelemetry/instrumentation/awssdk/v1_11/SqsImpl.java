/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

final class SqsImpl {
  static {
    // Force loading of SQS class; this ensures that an exception is thrown at this point when the
    // SQS library is not present, which will cause SqsAccess to have enabled=false in library mode.
    @SuppressWarnings("unused")
    String ensureLoadedDummy = AmazonSQS.class.getName();
  }

  private SqsImpl() {}

  static boolean afterResponse(
      Request<?> request,
      Response<?> response,
      Instrumenter<Request<?>, Response<?>> consumerInstrumenter) {
    if (response.getAwsResponse() instanceof ReceiveMessageResult) {
      afterConsumerResponse(request, response, consumerInstrumenter);
      return true;
    }
    return false;
  }

  private static void afterConsumerResponse(
      Request<?> request,
      Response<?> response,
      Instrumenter<Request<?>, Response<?>> consumerInstrumenter) {

    ReceiveMessageResult receiveMessageResult = (ReceiveMessageResult) response.getAwsResponse();

    Tracer tracer =
        GlobalOpenTelemetry.get().getTracer("io.opentelemetry.aws-sdk-2.2");

    SpanBuilder spanBuilder =
        tracer.spanBuilder("AmazonSQS receive").setSpanKind(SpanKind.CONSUMER);
    spanBuilder.setAttribute(SemanticAttributes.MESSAGING_OPERATION,
        SemanticAttributes.MessagingOperationValues.RECEIVE);
    spanBuilder.setAttribute(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS");

    long totalPayloadSizeInBytes = 0;

    for (Message message: receiveMessageResult.getMessages()) {
      SpanContext spanContext  = Span.fromContext(getParentContext(message)).getSpanContext();

      if (spanContext.isValid()) {
        spanBuilder.addLink(spanContext);
      }

      totalPayloadSizeInBytes += message.getBody().length();
    }

    spanBuilder.setAttribute(SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES, totalPayloadSizeInBytes);

    if (consumerInstrumenter.shouldStart(io.opentelemetry.context.Context.root(), request)) {
      Span consumerSpan = spanBuilder.startSpan();
      consumerSpan.end();
    }

  }

  public static Context getParentContext(Message message) {
    return SqsParentContext.ofSystemAttributes(message.getAttributes());
  }

  static boolean beforeMarshalling(AmazonWebServiceRequest rawRequest) {
    if (rawRequest instanceof ReceiveMessageRequest) {
      ReceiveMessageRequest request = (ReceiveMessageRequest) rawRequest;
      if (!request.getAttributeNames().contains(SqsParentContext.AWS_TRACE_SYSTEM_ATTRIBUTE)) {
        request.withAttributeNames(SqsParentContext.AWS_TRACE_SYSTEM_ATTRIBUTE);
      }
      return true;
    }
    return false;
  }
}

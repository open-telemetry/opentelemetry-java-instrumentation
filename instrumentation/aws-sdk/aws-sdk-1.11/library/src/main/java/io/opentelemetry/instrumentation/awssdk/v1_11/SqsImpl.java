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
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

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

  /** Create and close CONSUMER span for each message consumed. */
  private static void afterConsumerResponse(
      Request<?> request,
      Response<?> response,
      Instrumenter<Request<?>, Response<?>> consumerInstrumenter) {
    ReceiveMessageResult receiveMessageResult = (ReceiveMessageResult) response.getAwsResponse();
    for (Message message : receiveMessageResult.getMessages()) {
      createConsumerSpan(message, request, response, consumerInstrumenter);
    }
  }

  private static void createConsumerSpan(
      Message message,
      Request<?> request,
      Response<?> response,
      Instrumenter<Request<?>, Response<?>> consumerInstrumenter) {
    Context parentContext = SqsParentContext.ofSystemAttributes(message.getAttributes());
    Context context = consumerInstrumenter.start(parentContext, request);
    consumerInstrumenter.end(context, request, response, null);
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

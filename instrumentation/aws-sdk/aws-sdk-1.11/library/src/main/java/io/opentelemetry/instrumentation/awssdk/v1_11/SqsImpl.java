/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.MessageAttributeValue;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageResult;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import io.opentelemetry.instrumentation.api.internal.Timer;
import java.lang.reflect.Field;
import java.util.Map;

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
      Timer timer,
      Context parentContext,
      TracingRequestHandler requestHandler) {
    if (response.getAwsResponse() instanceof ReceiveMessageResult) {
      afterConsumerResponse(request, response, timer, parentContext, requestHandler);
      return true;
    }
    return false;
  }

  private static void afterConsumerResponse(
      Request<?> request,
      Response<?> response,
      Timer timer,
      Context parentContext,
      TracingRequestHandler requestHandler) {
    ReceiveMessageResult receiveMessageResult = (ReceiveMessageResult) response.getAwsResponse();
    if (receiveMessageResult.getMessages().isEmpty()) {
      return;
    }

    Instrumenter<SqsReceiveRequest, Response<?>> consumerReceiveInstrumenter =
        requestHandler.getConsumerReceiveInstrumenter();
    Instrumenter<SqsProcessRequest, Response<?>> consumerProcessInstrumenter =
        requestHandler.getConsumerProcessInstrumenter();

    Context receiveContext = null;
    SqsReceiveRequest receiveRequest =
        SqsReceiveRequest.create(request, SqsMessageImpl.wrap(receiveMessageResult.getMessages()));
    if (timer != null && consumerReceiveInstrumenter.shouldStart(parentContext, receiveRequest)) {
      receiveContext =
          InstrumenterUtil.startAndEnd(
              consumerReceiveInstrumenter,
              parentContext,
              receiveRequest,
              response,
              null,
              timer.startTime(),
              timer.now());
    }

    addTracing(
        receiveMessageResult, request, response, consumerProcessInstrumenter, receiveContext);
  }

  private static final Field messagesField = getMessagesField();

  private static Field getMessagesField() {
    try {
      Field field = ReceiveMessageResult.class.getDeclaredField("messages");
      field.setAccessible(true);
      return field;
    } catch (Exception e) {
      return null;
    }
  }

  private static void addTracing(
      ReceiveMessageResult receiveMessageResult,
      Request<?> request,
      Response<?> response,
      Instrumenter<SqsProcessRequest, Response<?>> consumerProcessInstrumenter,
      Context receiveContext) {
    if (messagesField == null) {
      return;
    }
    // replace Messages list inside ReceiveMessageResult with a tracing list that creates process
    // spans as the list is iterated
    try {
      messagesField.set(
          receiveMessageResult,
          TracingList.wrap(
              receiveMessageResult.getMessages(),
              consumerProcessInstrumenter,
              request,
              response,
              receiveContext));
    } catch (IllegalAccessException ignored) {
      // should not happen, we call setAccessible on the field
    }
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

  static String getMessageAttribute(Request<?> request, String name) {
    if (request.getOriginalRequest() instanceof SendMessageRequest) {
      Map<String, MessageAttributeValue> map =
          ((SendMessageRequest) request.getOriginalRequest()).getMessageAttributes();
      MessageAttributeValue value = map.get(name);
      if (value != null) {
        return value.getStringValue();
      }
    }
    return null;
  }

  static String getMessageId(Response<?> response) {
    if (response != null && response.getAwsResponse() instanceof SendMessageResult) {
      return ((SendMessageResult) response.getAwsResponse()).getMessageId();
    }
    return null;
  }
}

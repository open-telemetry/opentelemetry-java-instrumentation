/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.DeleteMessageBatchRequest;
import com.amazonaws.services.sqs.model.MessageAttributeValue;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.SendMessageBatchRequest;
import com.amazonaws.services.sqs.model.SendMessageBatchRequestEntry;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageResult;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.contrib.awsxray.propagator.AwsXrayPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import io.opentelemetry.instrumentation.api.internal.Timer;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class SqsImpl {
  private static final VirtualField<SendMessageBatchRequest, Context[]> batchMessageContexts =
      VirtualField.find(SendMessageBatchRequest.class, Context[].class);

  static {
    // Force loading of SQS class; this ensures that an exception is thrown at this point when the
    // SQS library is not present, which will cause SqsAccess to have enabled=false in library mode.
    @SuppressWarnings("unused")
    String ensureLoadedDummy = AmazonSQS.class.getName();
  }

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

    Context processParentContext = emitStableMessagingSemconv() ? parentContext : receiveContext;
    addTracing(
        receiveMessageResult, request, response, consumerProcessInstrumenter, processParentContext);
  }

  @Nullable private static final Field messagesField = getMessagesField();

  @Nullable
  private static Field getMessagesField() {
    try {
      Field field = ReceiveMessageResult.class.getDeclaredField("messages");
      field.setAccessible(true);
      return field;
    } catch (Exception ignored) {
      return null;
    }
  }

  private static void addTracing(
      ReceiveMessageResult receiveMessageResult,
      Request<?> request,
      Response<?> response,
      Instrumenter<SqsProcessRequest, Response<?>> consumerProcessInstrumenter,
      @Nullable Context processParentContext) {
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
              processParentContext));
    } catch (IllegalAccessException ignored) {
      // should not happen, we call setAccessible on the field
    }
  }

  static AmazonWebServiceRequest beforeMarshalling(
      AmazonWebServiceRequest rawRequest,
      Instrumenter<SqsCreateRequest, Void> producerCreateInstrumenter,
      boolean messageCreateSpansEnabled) {
    if (rawRequest instanceof ReceiveMessageRequest) {
      ReceiveMessageRequest request = (ReceiveMessageRequest) rawRequest;
      if (!request.getAttributeNames().contains(SqsParentContext.AWS_TRACE_SYSTEM_ATTRIBUTE)) {
        request.withAttributeNames(SqsParentContext.AWS_TRACE_SYSTEM_ATTRIBUTE);
      }
      if (emitStableMessagingSemconv()
          && !request
              .getMessageAttributeNames()
              .contains(SqsParentContext.AWS_TRACE_MESSAGE_ATTRIBUTE)) {
        request.withMessageAttributeNames(SqsParentContext.AWS_TRACE_MESSAGE_ATTRIBUTE);
      }
      return request;
    }
    if (rawRequest instanceof SendMessageBatchRequest
        && emitStableMessagingSemconv()
        && messageCreateSpansEnabled) {
      return injectBatchCreationContexts(
          (SendMessageBatchRequest) rawRequest, producerCreateInstrumenter);
    }
    return rawRequest;
  }

  private static SendMessageBatchRequest injectBatchCreationContexts(
      SendMessageBatchRequest request,
      Instrumenter<SqsCreateRequest, Void> producerCreateInstrumenter) {
    SendMessageBatchRequest preparedRequest = request.clone();
    List<SendMessageBatchRequestEntry> preparedEntries = new ArrayList<>();
    List<Context> creationContexts = new ArrayList<>();
    Context parentContext = Context.current().with(Span.getInvalid());
    TextMapPropagator xrayPropagator = AwsXrayPropagator.getInstance();
    for (SendMessageBatchRequestEntry entry : request.getEntries()) {
      SendMessageBatchRequestEntry preparedEntry = entry.clone();
      Map<String, MessageAttributeValue> attributes = entry.getMessageAttributes();
      Context customCreationContext = SqsParentContext.ofMessageAttributes(toStringMap(attributes));
      if (Span.fromContext(customCreationContext).getSpanContext().isValid()) {
        creationContexts.add(customCreationContext);
        preparedEntries.add(preparedEntry);
        continue;
      }
      if (attributes.containsKey(SqsParentContext.AWS_TRACE_MESSAGE_ATTRIBUTE)
          || attributes.size() >= 10) {
        preparedEntries.add(preparedEntry);
        continue;
      }

      SqsCreateRequest createRequest =
          new SqsCreateRequest(request.getQueueUrl(), toStringMap(attributes));
      if (!producerCreateInstrumenter.shouldStart(parentContext, createRequest)) {
        preparedEntries.add(preparedEntry);
        continue;
      }

      Context creationContext = producerCreateInstrumenter.start(parentContext, createRequest);
      creationContexts.add(creationContext);
      Map<String, MessageAttributeValue> updatedAttributes = new HashMap<>(attributes);
      xrayPropagator.inject(
          creationContext,
          updatedAttributes,
          (carrier, key, value) ->
              carrier.put(
                  key, new MessageAttributeValue().withDataType("String").withStringValue(value)));
      preparedEntry.setMessageAttributes(updatedAttributes);
      producerCreateInstrumenter.end(creationContext, createRequest, null, null);
      preparedEntries.add(preparedEntry);
    }
    preparedRequest.setEntries(preparedEntries);
    batchMessageContexts.set(preparedRequest, creationContexts.toArray(new Context[0]));
    return preparedRequest;
  }

  static boolean isBatchRequest(Request<?> request) {
    return request.getOriginalRequest() instanceof SendMessageBatchRequest;
  }

  static List<Context> getBatchMessageContexts(Request<?> request) {
    if (!(request.getOriginalRequest() instanceof SendMessageBatchRequest)) {
      return new ArrayList<>();
    }
    Context[] contexts =
        batchMessageContexts.get((SendMessageBatchRequest) request.getOriginalRequest());
    return contexts != null ? asList(contexts) : new ArrayList<>();
  }

  private static Map<String, String> toStringMap(
      Map<String, MessageAttributeValue> messageAttributes) {
    Map<String, String> result = new HashMap<>();
    messageAttributes.forEach((key, value) -> result.put(key, value.getStringValue()));
    return result;
  }

  @Nullable
  static Long getBatchMessageCount(Request<?> request) {
    if (request.getOriginalRequest() instanceof SendMessageBatchRequest) {
      return (long) ((SendMessageBatchRequest) request.getOriginalRequest()).getEntries().size();
    } else if (request.getOriginalRequest() instanceof DeleteMessageBatchRequest) {
      return (long) ((DeleteMessageBatchRequest) request.getOriginalRequest()).getEntries().size();
    }
    return null;
  }

  @Nullable
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

  static Collection<String> getMessageAttributeNames(Request<?> request) {
    if (request.getOriginalRequest() instanceof SendMessageRequest) {
      // the request is owned by the caller, so its attribute names are snapshotted
      return new ArrayList<>(
          ((SendMessageRequest) request.getOriginalRequest()).getMessageAttributes().keySet());
    }
    return emptyList();
  }

  @Nullable
  static String getMessageId(@Nullable Response<?> response) {
    if (response != null && response.getAwsResponse() instanceof SendMessageResult) {
      return ((SendMessageResult) response.getAwsResponse()).getMessageId();
    }
    return null;
  }

  private SqsImpl() {}
}

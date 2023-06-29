/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.SdkResponse;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

// this class is only used from SqsAccess from method with @NoMuzzle annotation
final class SqsImpl {
  static {
    // Force loading of SqsClient; this ensures that an exception is thrown at this point when the
    // SQS library is not present, which will cause SqsAccess to have enabled=false in library mode.
    @SuppressWarnings("unused")
    String ensureLoadedDummy = SqsClient.class.getName();
  }

  private SqsImpl() {}

  static boolean afterReceiveMessageExecution(
      Context.AfterExecution context,
      ExecutionAttributes executionAttributes,
      TracingExecutionInterceptor config) {

    SdkResponse rawResponse = context.response();
    if (!(rawResponse instanceof ReceiveMessageResponse)) {
      return false;
    }

    ReceiveMessageResponse response = (ReceiveMessageResponse) rawResponse;
    SdkHttpResponse httpResponse = context.httpResponse();
    for (Message message : response.messages()) {
      createConsumerSpan(message, httpResponse, executionAttributes, config);
    }

    return true;
  }

  private static void createConsumerSpan(
      Message message,
      SdkHttpResponse httpResponse,
      ExecutionAttributes executionAttributes,
      TracingExecutionInterceptor config) {

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

    Instrumenter<ExecutionAttributes, SdkHttpResponse> consumerInstrumenter =
        config.getConsumerInstrumenter();
    if (consumerInstrumenter.shouldStart(parentContext, executionAttributes)) {
      io.opentelemetry.context.Context context =
          consumerInstrumenter.start(parentContext, executionAttributes);

      // TODO: Even if we keep HTTP attributes (see afterMarshalling), does it make sense here
      //  per-message?
      // TODO: Should we really create root spans if we can't extract anything, or should we attach
      //  to the current context?
      consumerInstrumenter.end(context, executionAttributes, httpResponse, null);
    }
  }

  @Nullable
  static SdkRequest modifyRequest(
      SdkRequest request,
      io.opentelemetry.context.Context otelContext,
      boolean useXrayPropagator,
      TextMapPropagator messagingPropagator) {
    if (request instanceof ReceiveMessageRequest) {
      return modifyReceiveMessageRequest(
          (ReceiveMessageRequest) request, useXrayPropagator, messagingPropagator);
    } else if (messagingPropagator != null) {
      if (request instanceof SendMessageRequest) {
        return injectIntoSendMessageRequest(
            (SendMessageRequest) request, otelContext, messagingPropagator);
      } else if (request instanceof SendMessageBatchRequest) {
        return injectIntoSendMessageBatchRequest(
            (SendMessageBatchRequest) request, otelContext, messagingPropagator);
      }
    }
    return null;
  }

  private static SdkRequest injectIntoSendMessageBatchRequest(
      SendMessageBatchRequest request,
      io.opentelemetry.context.Context otelContext,
      TextMapPropagator messagingPropagator) {
    ArrayList<SendMessageBatchRequestEntry> entries = new ArrayList<>(request.entries());
    for (int i = 0; i < entries.size(); ++i) {
      SendMessageBatchRequestEntry entry = entries.get(i);
      Map<String, MessageAttributeValue> messageAttributes =
          new HashMap<>(entry.messageAttributes());

      // TODO: Per https://github.com/open-telemetry/oteps/pull/220, each message should get
      //  a separate context. We don't support this yet, also because it would be inconsistent
      //  with the header-based X-Ray propagation
      //  (probably could override it here by setting the X-Ray message system attribute)
      if (injectIntoMessageAttributes(messageAttributes, otelContext, messagingPropagator)) {
        entries.set(i, entry.toBuilder().messageAttributes(messageAttributes).build());
      }
    }
    return request.toBuilder().entries(entries).build();
  }

  private static SdkRequest injectIntoSendMessageRequest(
      SendMessageRequest request,
      io.opentelemetry.context.Context otelContext,
      TextMapPropagator messagingPropagator) {
    Map<String, MessageAttributeValue> messageAttributes =
        new HashMap<>(request.messageAttributes());
    if (!injectIntoMessageAttributes(messageAttributes, otelContext, messagingPropagator)) {
      return request;
    }
    return request.toBuilder().messageAttributes(messageAttributes).build();
  }

  private static boolean injectIntoMessageAttributes(
      Map<String, MessageAttributeValue> messageAttributes,
      io.opentelemetry.context.Context otelContext,
      TextMapPropagator messagingPropagator) {
    messagingPropagator.inject(
        otelContext,
        messageAttributes,
        (carrier, k, v) -> {
          carrier.put(k, MessageAttributeValue.builder().stringValue(v).dataType("String").build());
        });

    // Return whether the injection resulted in an attribute count that is still supported.
    // See
    // https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-message-metadata.html#sqs-message-attributes
    return messageAttributes.size() <= 10;
  }

  private static SdkRequest modifyReceiveMessageRequest(
      ReceiveMessageRequest request,
      boolean useXrayPropagator,
      TextMapPropagator messagingPropagator) {
    boolean hasXrayAttribute = true;
    List<String> existingAttributeNames = null;
    if (useXrayPropagator) {
      existingAttributeNames = request.attributeNamesAsStrings();
      hasXrayAttribute =
          existingAttributeNames.contains(SqsParentContext.AWS_TRACE_SYSTEM_ATTRIBUTE);
    }

    boolean hasMessageAttribute = true;
    List<String> existingMessageAttributeNames = null;
    if (messagingPropagator != null) {
      existingMessageAttributeNames = request.messageAttributeNames();
      hasMessageAttribute = existingMessageAttributeNames.containsAll(messagingPropagator.fields());
    }

    if (hasMessageAttribute && hasXrayAttribute) {
      return request;
    }

    ReceiveMessageRequest.Builder builder = request.toBuilder();
    if (!hasXrayAttribute) {
      List<String> attributeNames = new ArrayList<>(existingAttributeNames);
      attributeNames.add(SqsParentContext.AWS_TRACE_SYSTEM_ATTRIBUTE);
      builder.attributeNamesWithStrings(attributeNames);
    }
    if (messagingPropagator != null) {
      List<String> messageAttributeNames = new ArrayList<>(existingMessageAttributeNames);
      for (String field : messagingPropagator.fields()) {
        if (!existingMessageAttributeNames.contains(field)) {
          messageAttributeNames.add(field);
        }
      }
      builder.messageAttributeNames(messageAttributeNames);
    }
    return builder.build();
  }
}

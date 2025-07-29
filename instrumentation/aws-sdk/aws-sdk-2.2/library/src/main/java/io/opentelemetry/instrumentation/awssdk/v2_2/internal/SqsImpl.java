/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.TracingExecutionInterceptor.SDK_HTTP_REQUEST_ATTRIBUTE;
import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.TracingExecutionInterceptor.SDK_REQUEST_ATTRIBUTE;

import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import io.opentelemetry.instrumentation.api.internal.Timer;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.SdkResponse;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
// this class is only used from SqsAccess from method with @NoMuzzle annotation
public final class SqsImpl {
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
      TracingExecutionInterceptor config,
      Timer timer) {

    SdkResponse rawResponse = context.response();
    if (!(rawResponse instanceof ReceiveMessageResponse)) {
      return false;
    }

    ReceiveMessageResponse response = (ReceiveMessageResponse) rawResponse;
    if (response.messages().isEmpty()) {
      return false;
    }

    io.opentelemetry.context.Context parentContext =
        TracingExecutionInterceptor.getParentContext(executionAttributes);
    Instrumenter<SqsReceiveRequest, Response> consumerReceiveInstrumenter =
        config.getConsumerReceiveInstrumenter();
    io.opentelemetry.context.Context receiveContext = null;
    SqsReceiveRequest receiveRequest =
        SqsReceiveRequest.create(executionAttributes, SqsMessageImpl.wrap(response.messages()));
    if (timer != null && consumerReceiveInstrumenter.shouldStart(parentContext, receiveRequest)) {
      receiveContext =
          InstrumenterUtil.startAndEnd(
              consumerReceiveInstrumenter,
              parentContext,
              receiveRequest,
              new Response(context.httpResponse(), response),
              null,
              timer.startTime(),
              timer.now());
    }
    // copy ExecutionAttributes as these will get cleared before the process spans are created
    ExecutionAttributes copy = new ExecutionAttributes();
    copy.putAttribute(
        SDK_HTTP_REQUEST_ATTRIBUTE, executionAttributes.getAttribute(SDK_HTTP_REQUEST_ATTRIBUTE));
    copy.putAttribute(
        SDK_REQUEST_ATTRIBUTE, executionAttributes.getAttribute(SDK_REQUEST_ATTRIBUTE));
    copy.putAttribute(
        SdkExecutionAttribute.SERVICE_NAME,
        executionAttributes.getAttribute(SdkExecutionAttribute.SERVICE_NAME));
    copy.putAttribute(
        SdkExecutionAttribute.OPERATION_NAME,
        executionAttributes.getAttribute(SdkExecutionAttribute.OPERATION_NAME));

    TracingList tracingList =
        TracingList.wrap(
            response.messages(),
            config.getConsumerProcessInstrumenter(),
            copy,
            new Response(context.httpResponse(), response),
            config,
            receiveContext);

    // store tracing list in context so that our proxied SqsClient/SqsAsyncClient could pick it up
    SqsTracingContext.set(parentContext, tracingList);

    return true;
  }

  private static final Field messagesField = getMessagesField();

  private static Field getMessagesField() {
    try {
      Field field = ReceiveMessageResponse.class.getDeclaredField("messages");
      field.setAccessible(true);
      return field;
    } catch (Exception e) {
      return null;
    }
  }

  public static void setMessages(
      ReceiveMessageResponse receiveMessageResponse, List<Message> messages) {
    try {
      messagesField.set(receiveMessageResponse, messages);
    } catch (IllegalAccessException ignored) {
      // should not happen, we call setAccessible on the field
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

  static boolean isSqsProducerRequest(SdkRequest request) {
    return request instanceof SendMessageRequest || request instanceof SendMessageBatchRequest;
  }

  static String getQueueUrl(SdkRequest request) {
    if (request instanceof SendMessageRequest) {
      return ((SendMessageRequest) request).queueUrl();
    } else if (request instanceof SendMessageBatchRequest) {
      return ((SendMessageBatchRequest) request).queueUrl();
    } else if (request instanceof ReceiveMessageRequest) {
      return ((ReceiveMessageRequest) request).queueUrl();
    }
    return null;
  }

  static String getMessageAttribute(SdkRequest request, String name) {
    if (request instanceof SendMessageRequest) {
      MessageAttributeValue value = ((SendMessageRequest) request).messageAttributes().get(name);
      return value != null ? value.stringValue() : null;
    }
    return null;
  }

  static String getMessageId(SdkResponse response) {
    if (response instanceof SendMessageResponse) {
      return ((SendMessageResponse) response).messageId();
    }
    return null;
  }

  public static SqsClient wrap(SqsClient sqsClient) {
    // proxy SqsClient so we could replace the messages list in ReceiveMessageResponse returned from
    // receiveMessage call
    return (SqsClient)
        Proxy.newProxyInstance(
            sqsClient.getClass().getClassLoader(),
            new Class<?>[] {SqsClient.class},
            (proxy, method, args) -> {
              if ("receiveMessage".equals(method.getName())) {
                SqsTracingContext sqsTracingContext = new SqsTracingContext();
                try (Scope ignored =
                    io.opentelemetry.context.Context.current()
                        .with(sqsTracingContext)
                        .makeCurrent()) {
                  Object result = invokeProxyMethod(method, sqsClient, args);
                  TracingList tracingList = sqsTracingContext.get();
                  if (tracingList != null) {
                    ReceiveMessageResponse response = (ReceiveMessageResponse) result;
                    SqsImpl.setMessages(response, tracingList);
                    return response;
                  }
                  return result;
                }
              } else {
                return invokeProxyMethod(method, sqsClient, args);
              }
            });
  }

  @SuppressWarnings("unchecked")
  public static SqsAsyncClient wrap(SqsAsyncClient sqsClient) {
    // proxy SqsAsyncClient so we could replace the messages list in ReceiveMessageResponse returned
    // from receiveMessage call
    return (SqsAsyncClient)
        Proxy.newProxyInstance(
            sqsClient.getClass().getClassLoader(),
            new Class<?>[] {SqsAsyncClient.class},
            (proxy, method, args) -> {
              if ("receiveMessage".equals(method.getName())) {
                SqsTracingContext sqsTracingContext = new SqsTracingContext();
                try (Scope ignored =
                    io.opentelemetry.context.Context.current()
                        .with(sqsTracingContext)
                        .makeCurrent()) {
                  Object result = invokeProxyMethod(method, sqsClient, args);
                  CompletableFuture<ReceiveMessageResponse> originalFuture =
                      (CompletableFuture<ReceiveMessageResponse>) result;
                  CompletableFuture<ReceiveMessageResponse> resultFuture =
                      new CompletableFuture<>();
                  originalFuture.whenComplete(
                      (response, throwable) -> {
                        if (throwable != null) {
                          resultFuture.completeExceptionally(throwable);
                        } else {
                          TracingList tracingList = sqsTracingContext.get();
                          if (tracingList != null) {
                            SqsImpl.setMessages(response, tracingList);
                          }
                          resultFuture.complete(response);
                        }
                      });

                  return resultFuture;
                } catch (InvocationTargetException exception) {
                  throw exception.getCause();
                }
              } else {
                return invokeProxyMethod(method, sqsClient, args);
              }
            });
  }

  private static Object invokeProxyMethod(Method method, Object target, Object[] args)
      throws Throwable {
    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException exception) {
      throw exception.getCause();
    }
  }
}

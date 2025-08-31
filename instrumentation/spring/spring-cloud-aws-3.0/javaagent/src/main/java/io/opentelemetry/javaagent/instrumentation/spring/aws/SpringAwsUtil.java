/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.aws;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.awssdk.v2_2.internal.Response;
import io.opentelemetry.instrumentation.awssdk.v2_2.internal.SqsMessage;
import io.opentelemetry.instrumentation.awssdk.v2_2.internal.SqsMessageImpl;
import io.opentelemetry.instrumentation.awssdk.v2_2.internal.SqsParentContext;
import io.opentelemetry.instrumentation.awssdk.v2_2.internal.SqsProcessRequest;
import io.opentelemetry.instrumentation.awssdk.v2_2.internal.TracingExecutionInterceptor;
import io.opentelemetry.instrumentation.awssdk.v2_2.internal.TracingList;
import java.util.Collection;
import org.springframework.messaging.Message;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;

public final class SpringAwsUtil {
  private static final ThreadLocal<TracingList> context = new ThreadLocal<>();
  private static final VirtualField<Message<?>, TracingContext> tracingContextField =
      VirtualField.find(Message.class, TracingContext.class);

  // put the TracingList into thread local, so we can use it in attachTracingState method
  public static void initialize(Collection<?> messages) {
    if (messages instanceof TracingList tracingList) {
      // disable tracing int the iterator of TracingList, we'll do the tracing when message handler
      // is called
      tracingList.disableTracing();
      context.set(tracingList);
    }
  }

  public static void clear() {
    context.remove();
  }

  // copy tracing state from the sqs message to spring message, we'll use that state when the
  // message handler is called
  public static void attachTracingState(Object originalMessage, Message<?> convertedMessage) {
    TracingList tracingList = context.get();
    if (tracingList == null) {
      return;
    }
    if (!(originalMessage instanceof software.amazon.awssdk.services.sqs.model.Message message)) {
      return;
    }

    tracingContextField.set(convertedMessage, new TracingContext(tracingList, message));
  }

  public static void copyTracingState(Message<?> original, Message<?> transformed) {
    if (original == transformed) {
      return;
    }

    tracingContextField.set(transformed, tracingContextField.get(original));
  }

  public static MessageScope handleMessage(Message<?> message) {
    TracingContext tracingContext = tracingContextField.get(message);
    if (tracingContext == null) {
      return null;
    }

    return tracingContext.trace();
  }

  // restore context from the first message of the batch
  public static Scope handleBatch(Collection<Message<?>> messages) {
    if (messages == null || messages.isEmpty()) {
      return null;
    }
    Message<?> message = messages.iterator().next();
    TracingContext tracingContext = tracingContextField.get(message);
    if (tracingContext == null) {
      return null;
    }
    SqsMessage wrappedMessage = SqsMessageImpl.wrap(tracingContext.sqsMessage);
    Context parentContext = tracingContext.receiveContext;
    if (parentContext == null) {
      parentContext = SqsParentContext.ofMessage(wrappedMessage, tracingContext.config);
    }
    return parentContext.makeCurrent();
  }

  public static class MessageScope {
    final Instrumenter<SqsProcessRequest, Response> instrumenter;
    final Context context;
    final SqsProcessRequest request;
    final Response response;
    final Scope scope;

    MessageScope(
        Instrumenter<SqsProcessRequest, Response> instrumenter,
        Context context,
        SqsProcessRequest request,
        Response response) {
      this.instrumenter = instrumenter;
      this.context = context;
      this.request = request;
      this.response = response;
      this.scope = context.makeCurrent();
    }

    public void close(Throwable throwable) {
      scope.close();
      instrumenter.end(context, request, response, throwable);
    }
  }

  private static class TracingContext {
    final ExecutionAttributes request;
    final Response response;
    final Instrumenter<SqsProcessRequest, Response> instrumenter;
    final TracingExecutionInterceptor config;
    final Context receiveContext;
    final software.amazon.awssdk.services.sqs.model.Message sqsMessage;

    TracingContext(
        TracingList tracingList, software.amazon.awssdk.services.sqs.model.Message sqsMessage) {
      this.request = tracingList.getRequest();
      this.response = tracingList.getResponse();
      this.instrumenter = tracingList.getInstrumenter();
      this.config = tracingList.getConfig();
      this.receiveContext = tracingList.getReceiveContext();
      this.sqsMessage = sqsMessage;
    }

    MessageScope trace() {
      SqsMessage wrappedMessage = SqsMessageImpl.wrap(sqsMessage);
      Context parentContext = receiveContext;
      if (parentContext == null) {
        parentContext = SqsParentContext.ofMessage(wrappedMessage, config);
      }
      SqsProcessRequest processRequest = SqsProcessRequest.create(request, wrappedMessage);
      if (!instrumenter.shouldStart(parentContext, processRequest)) {
        return null;
      }
      Context context = instrumenter.start(parentContext, processRequest);
      return new MessageScope(instrumenter, context, processRequest, response);
    }
  }

  private SpringAwsUtil() {}
}

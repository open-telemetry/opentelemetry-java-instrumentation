/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.cloud.aws.v3_0;

import static io.opentelemetry.javaagent.instrumentation.spring.cloud.aws.v3_0.SpringAwsSqsSingletons.batchProcessInstrumenter;

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
import javax.annotation.Nullable;
import org.springframework.messaging.Message;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;

public class SpringAwsUtil {
  private static final ThreadLocal<TracingList> context = new ThreadLocal<>();
  private static final VirtualField<Message<?>, TracingContext> tracingContextField =
      VirtualField.find(Message.class, TracingContext.class);

  // put the TracingList into thread local, so we can use it in attachTracingState method
  public static void initialize(Collection<?> messages) {
    if (messages instanceof TracingList tracingList) {
      // disable tracing in the iterator of TracingList, we'll do the tracing when message handler
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

  @Nullable
  public static MessageScope handleMessage(Message<?> message) {
    TracingContext tracingContext = tracingContextField.get(message);
    if (tracingContext == null) {
      return null;
    }

    return tracingContext.trace();
  }

  @Nullable
  public static BatchMessageScope handleBatch(Collection<Message<?>> messages) {
    if (messages.isEmpty()) {
      return null;
    }

    // Check if the batch has any tracing context before starting
    Message<?> firstMessage = messages.iterator().next();
    TracingContext tracingContext = tracingContextField.get(firstMessage);
    if (tracingContext == null) {
      return null;
    }

    // Use the receive context as parent when receive telemetry is enabled
    // (otel.instrumentation.messaging.experimental.receive-telemetry.enabled=true),
    // otherwise use Context.current() which starts a new trace for the batch.
    Context parentContext = tracingContext.getReceiveContext();
    if (parentContext == null) {
      parentContext = Context.current();
    }
    if (!batchProcessInstrumenter().shouldStart(parentContext, messages)) {
      return null;
    }
    Context batchContext = batchProcessInstrumenter().start(parentContext, messages);

    for (Message<?> msg : messages) {
      TracingContext tc = tracingContextField.get(msg);
      if (tc != null) {
        tc.batchProcessContext = batchContext;
      }
    }

    return new BatchMessageScope(batchProcessInstrumenter(), batchContext, messages);
  }

  @Nullable
  public static Scope restoreBatchContext(Collection<Message<?>> messages) {
    if (messages.isEmpty()) {
      return null;
    }
    Message<?> message = messages.iterator().next();
    TracingContext tracingContext = tracingContextField.get(message);
    if (tracingContext == null || tracingContext.batchProcessContext == null) {
      return null;
    }
    return tracingContext.batchProcessContext.makeCurrent();
  }

  public static class BatchMessageScope {
    private final Instrumenter<Collection<Message<?>>, Void> instrumenter;
    private final Context context;
    private final Collection<Message<?>> request;
    private final Scope scope;

    private BatchMessageScope(
        Instrumenter<Collection<Message<?>>, Void> instrumenter,
        Context context,
        Collection<Message<?>> request) {
      this.instrumenter = instrumenter;
      this.context = context;
      this.request = request;
      this.scope = context.makeCurrent();
    }

    public void close(@Nullable Throwable throwable) {
      scope.close();
      instrumenter.end(context, request, null, throwable);
    }
  }

  public static class MessageScope {
    private final Instrumenter<SqsProcessRequest, Response> instrumenter;
    private final Context context;
    private final SqsProcessRequest request;
    private final Response response;
    private final Scope scope;

    private MessageScope(
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

    public void close(@Nullable Throwable throwable) {
      scope.close();
      instrumenter.end(context, request, response, throwable);
    }
  }

  // package-private so that SpringAwsSqsBatchProcessAttributesGetter and
  // SpringAwsSqsSingletons can access tracing state
  static class TracingContext {
    private final ExecutionAttributes request;
    private final Response response;
    private final Instrumenter<SqsProcessRequest, Response> instrumenter;
    private final TracingExecutionInterceptor config;
    @Nullable private final Context receiveContext;
    private final software.amazon.awssdk.services.sqs.model.Message sqsMessage;
    @Nullable Context batchProcessContext;

    TracingContext(
        TracingList tracingList, software.amazon.awssdk.services.sqs.model.Message sqsMessage) {
      this.request = tracingList.getRequest();
      this.response = tracingList.getResponse();
      this.instrumenter = tracingList.getInstrumenter();
      this.config = tracingList.getConfig();
      this.receiveContext = tracingList.getReceiveContext();
      this.sqsMessage = sqsMessage;
    }

    ExecutionAttributes getRequest() {
      return request;
    }

    TracingExecutionInterceptor getConfig() {
      return config;
    }

    @Nullable
    Context getReceiveContext() {
      return receiveContext;
    }

    software.amazon.awssdk.services.sqs.model.Message getSqsMessage() {
      return sqsMessage;
    }

    @Nullable
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
      this.batchProcessContext = context;
      return new MessageScope(instrumenter, context, processRequest, response);
    }
  }

  private SpringAwsUtil() {}
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.cloud.aws.v3_0;

import static io.opentelemetry.javaagent.instrumentation.spring.cloud.aws.v3_0.SpringAwsSqsSingletons.batchProcessInstrumenter;

import io.awspring.cloud.sqs.listener.ContainerOptions;
import io.awspring.cloud.sqs.listener.ListenerMode;
import io.awspring.cloud.sqs.listener.source.AbstractMessageConvertingMessageSource;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.ScopedThreadValue;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.awssdk.v2_2.internal.Response;
import io.opentelemetry.instrumentation.awssdk.v2_2.internal.SqsMessage;
import io.opentelemetry.instrumentation.awssdk.v2_2.internal.SqsParentContext;
import io.opentelemetry.instrumentation.awssdk.v2_2.internal.SqsProcessRequest;
import io.opentelemetry.instrumentation.awssdk.v2_2.internal.TracingExecutionInterceptor;
import io.opentelemetry.instrumentation.awssdk.v2_2.internal.TracingList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import org.springframework.messaging.Message;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;

public class SpringAwsUtil {
  private static final ScopedThreadValue<TracingList> currentTracingList =
      new ScopedThreadValue<>();
  private static final VirtualField<AbstractMessageConvertingMessageSource<?, ?>, ListenerMode>
      LISTENER_MODE =
          VirtualField.find(AbstractMessageConvertingMessageSource.class, ListenerMode.class);
  private static final VirtualField<Message<?>, TracingContext> TRACING_CONTEXT =
      VirtualField.find(Message.class, TracingContext.class);

  public static void setListenerMode(
      AbstractMessageConvertingMessageSource<?, ?> messageSource,
      ContainerOptions<?, ?> containerOptions) {
    LISTENER_MODE.set(messageSource, containerOptions.getListenerMode());
  }

  @Nullable
  public static TracingList initialize(
      AbstractMessageConvertingMessageSource<?, ?> messageSource, Collection<?> messages) {
    TracingList tracingList =
        messages instanceof TracingList currentTracingList ? currentTracingList : null;
    if (tracingList != null && LISTENER_MODE.get(messageSource) == ListenerMode.SINGLE_MESSAGE) {
      tracingList.markProcessingOwnedOutsideSqsSdk();
    }
    return currentTracingList.set(tracingList);
  }

  public static void restore(@Nullable TracingList previous) {
    currentTracingList.restore(previous);
  }

  // copy tracing state from the sqs message to spring message, we'll use that state when the
  // message handler is called
  public static void attachTracingState(Object originalMessage, Message<?> convertedMessage) {
    TracingList tracingList = currentTracingList.get();
    if (tracingList == null) {
      return;
    }
    if (!(originalMessage instanceof software.amazon.awssdk.services.sqs.model.Message message)) {
      return;
    }

    SqsMessage tracingMessage = tracingList.getTracingMessage(message);
    if (tracingMessage != null) {
      TRACING_CONTEXT.set(convertedMessage, new TracingContext(tracingList, tracingMessage));
    }
  }

  public static void copyTracingState(Message<?> original, Message<?> transformed) {
    if (original == transformed) {
      return;
    }

    TRACING_CONTEXT.set(transformed, TRACING_CONTEXT.get(original));
  }

  @Nullable
  public static ProcessingInvocation<?, ?> handleMessage(Message<?> message) {
    TracingContext tracingContext = TRACING_CONTEXT.get(message);
    if (tracingContext == null) {
      return null;
    }

    return tracingContext.trace();
  }

  @Nullable
  public static ProcessingInvocation<?, ?> handleBatchProcess(
      Collection<? extends Message<?>> messages) {
    List<TracingContext> tracingContexts = new ArrayList<>();
    List<SqsMessage> sqsMessages = new ArrayList<>();
    for (Message<?> message : messages) {
      TracingContext tracingContext = TRACING_CONTEXT.get(message);
      if (tracingContext != null) {
        tracingContexts.add(tracingContext);
        sqsMessages.add(tracingContext.sqsMessage);
      }
    }
    if (tracingContexts.isEmpty()) {
      return null;
    }

    Instrumenter<SpringAwsSqsBatchProcessRequest, Void> instrumenter = batchProcessInstrumenter();
    SpringAwsSqsBatchProcessRequest request =
        new SpringAwsSqsBatchProcessRequest(messages, sqsMessages);
    Context parentContext = Context.current();
    if (!instrumenter.shouldStart(parentContext, request)) {
      return null;
    }
    Context context = instrumenter.start(parentContext, request);
    // acknowledgement of the batch happens later, possibly on another thread
    for (TracingContext tracingContext : tracingContexts) {
      tracingContext.batchProcessContext = context;
    }
    return new ProcessingInvocation<>(instrumenter, context, request, null);
  }

  // restore context of the batch process span, or of the first message of the batch when there
  // was none
  @Nullable
  public static Scope handleBatch(Collection<Message<?>> messages) {
    if (messages.isEmpty()) {
      return null;
    }
    Message<?> message = messages.iterator().next();
    TracingContext tracingContext = TRACING_CONTEXT.get(message);
    if (tracingContext == null) {
      return null;
    }
    Context batchProcessContext = tracingContext.batchProcessContext;
    if (batchProcessContext != null) {
      return batchProcessContext.makeCurrent();
    }
    SqsMessage wrappedMessage = tracingContext.sqsMessage;
    Context parentContext = tracingContext.processParentContext;
    if (parentContext == null) {
      parentContext = wrappedMessage.getCreationContext();
    } else if (!Span.fromContext(parentContext).getSpanContext().isValid()) {
      parentContext =
          SqsParentContext.ofMessage(parentContext, wrappedMessage, tracingContext.config);
    }
    return parentContext.makeCurrent();
  }

  public static class ProcessingInvocation<REQUEST, RESPONSE> {
    private final Instrumenter<REQUEST, RESPONSE> instrumenter;
    private final Context context;
    private final REQUEST request;
    @Nullable private final RESPONSE response;
    private final Scope scope;

    private ProcessingInvocation(
        Instrumenter<REQUEST, RESPONSE> instrumenter,
        Context context,
        REQUEST request,
        @Nullable RESPONSE response) {
      this.instrumenter = instrumenter;
      this.context = context;
      this.request = request;
      this.response = response;
      this.scope = context.makeCurrent();
    }

    public void end(@Nullable Throwable throwable) {
      scope.close();
      instrumenter.end(context, request, response, throwable);
    }

    public void endWhenComplete(
        @Nullable CompletableFuture<?> future, @Nullable Throwable throwable) {
      scope.close();
      if (future == null || throwable != null) {
        instrumenter.end(context, request, response, throwable);
        return;
      }
      future.whenComplete(
          (unused, completionError) ->
              instrumenter.end(context, request, response, completionError));
    }
  }

  private static class TracingContext {
    private final ExecutionAttributes request;
    private final Response response;
    private final Instrumenter<SqsProcessRequest, Response> instrumenter;
    private final TracingExecutionInterceptor config;
    @Nullable private final Context processParentContext;
    private final SqsMessage sqsMessage;
    @Nullable private volatile Context batchProcessContext;

    private TracingContext(TracingList tracingList, SqsMessage sqsMessage) {
      this.request = tracingList.getRequest();
      this.response = tracingList.getResponse();
      this.instrumenter = tracingList.getInstrumenter();
      this.config = tracingList.getConfig();
      this.processParentContext = tracingList.getProcessParentContext();
      this.sqsMessage = sqsMessage;
    }

    @Nullable
    ProcessingInvocation<SqsProcessRequest, Response> trace() {
      SqsMessage wrappedMessage = sqsMessage;
      Context parentContext = processParentContext;
      if (parentContext == null) {
        parentContext = wrappedMessage.getCreationContext();
      }
      SqsProcessRequest processRequest = SqsProcessRequest.create(request, wrappedMessage);
      if (!instrumenter.shouldStart(parentContext, processRequest)) {
        return null;
      }
      Context context = instrumenter.start(parentContext, processRequest);
      return new ProcessingInvocation<>(instrumenter, context, processRequest, response);
    }
  }

  private SpringAwsUtil() {}
}

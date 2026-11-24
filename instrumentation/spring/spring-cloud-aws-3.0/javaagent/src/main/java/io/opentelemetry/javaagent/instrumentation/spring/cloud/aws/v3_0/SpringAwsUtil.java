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
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import org.springframework.messaging.Message;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;

public class SpringAwsUtil {
  private static final ScopedThreadValue<Collection<?>> currentReceivedMessages =
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
  public static Collection<?> initialize(
      AbstractMessageConvertingMessageSource<?, ?> messageSource, Collection<?> messages) {
    Collection<?> receivedMessages = null;
    if (messages instanceof TracingList tracingList) {
      if (LISTENER_MODE.get(messageSource) == ListenerMode.SINGLE_MESSAGE) {
        tracingList.markProcessingOwnedOutsideSqsSdk();
      }
      receivedMessages = tracingList;
    } else if (messages instanceof CombinedMessages) {
      receivedMessages = messages;
    }
    return currentReceivedMessages.set(receivedMessages);
  }

  public static void restore(@Nullable Collection<?> previous) {
    currentReceivedMessages.restore(previous);
  }

  // when a poll asks for more than 10 messages, spring combines the responses of several receive
  // calls into a new list, which loses the tracing lists of the responses; the combined list is
  // replaced with one that keeps the tracing list of each of its messages
  public static Collection<?> combineTracingState(
      Collection<?> firstBatch, Collection<?> secondBatch, Collection<?> combinedBatch) {
    if (!hasTracingState(firstBatch) && !hasTracingState(secondBatch)) {
      return combinedBatch;
    }
    CombinedMessages combinedMessages = new CombinedMessages(combinedBatch);
    combinedMessages.addTracingState(firstBatch);
    combinedMessages.addTracingState(secondBatch);
    return combinedMessages;
  }

  private static boolean hasTracingState(Collection<?> messages) {
    return messages instanceof TracingList || messages instanceof CombinedMessages;
  }

  // copy tracing state from the sqs message to spring message, we'll use that state when the
  // message handler is called
  public static void attachTracingState(Object originalMessage, Message<?> convertedMessage) {
    if (!(originalMessage instanceof software.amazon.awssdk.services.sqs.model.Message message)) {
      return;
    }
    Collection<?> receivedMessages = currentReceivedMessages.get();
    TracingList tracingList = null;
    if (receivedMessages instanceof TracingList currentTracingList) {
      tracingList = currentTracingList;
    } else if (receivedMessages instanceof CombinedMessages combinedMessages) {
      tracingList = combinedMessages.getTracingList(message);
    }
    if (tracingList == null) {
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
        new SpringAwsSqsBatchProcessRequest(
            messages.size(), sqsMessages, tracingContexts.get(0).request);
    Context parentContext = Context.current();
    Context context =
        instrumenter.shouldStart(parentContext, request)
            ? instrumenter.start(parentContext, request)
            : null;
    // acknowledgement of the batch happens later, possibly on another thread; a batch that is not
    // traced clears the context of an earlier batch of the same messages
    for (TracingContext tracingContext : tracingContexts) {
      tracingContext.batchProcessContext = context;
    }
    return context != null
        ? new ProcessingInvocation<>(instrumenter, context, request, null)
        : null;
  }

  // restore context of the batch process span when it processed all acknowledged messages, which
  // may come from several listener invocations, otherwise of the first acknowledged message
  @Nullable
  public static Scope handleBatch(Collection<Message<?>> messages) {
    if (messages.isEmpty()) {
      return null;
    }
    Iterator<Message<?>> iterator = messages.iterator();
    TracingContext tracingContext = TRACING_CONTEXT.get(iterator.next());
    if (tracingContext == null) {
      return null;
    }
    Context batchProcessContext = tracingContext.batchProcessContext;
    while (batchProcessContext != null && iterator.hasNext()) {
      TracingContext other = TRACING_CONTEXT.get(iterator.next());
      if (other == null || other.batchProcessContext != batchProcessContext) {
        batchProcessContext = null;
      }
    }
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
      future.whenComplete((unused, completionError) ->
          instrumenter.end(context, request, response, completionError));
    }
  }

  // the messages of several receive responses, with the tracing list of each message
  private static class CombinedMessages extends ArrayList<Object> {
    private static final long serialVersionUID = 1L;

    private final IdentityHashMap<Object, TracingList> tracingLists = new IdentityHashMap<>();

    private CombinedMessages(Collection<?> messages) {
      super(messages);
    }

    private void addTracingState(Collection<?> batch) {
      if (batch instanceof TracingList tracingList) {
        // iterating the tracing list would start sdk process spans
        for (int i = 0; i < tracingList.size(); i++) {
          tracingLists.put(tracingList.get(i), tracingList);
        }
      } else if (batch instanceof CombinedMessages combinedMessages) {
        tracingLists.putAll(combinedMessages.tracingLists);
      }
    }

    @Nullable
    private TracingList getTracingList(Object message) {
      return tracingLists.get(message);
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

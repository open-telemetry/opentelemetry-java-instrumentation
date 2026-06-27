/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.cloud.aws.v3_0;

import static java.util.Collections.emptyList;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.awssdk.v2_2.internal.Response;
import io.opentelemetry.instrumentation.awssdk.v2_2.internal.SqsMessage;
import io.opentelemetry.instrumentation.awssdk.v2_2.internal.SqsMessageImpl;
import io.opentelemetry.instrumentation.awssdk.v2_2.internal.SqsParentContext;
import io.opentelemetry.instrumentation.awssdk.v2_2.internal.SqsProcessRequest;
import io.opentelemetry.instrumentation.awssdk.v2_2.internal.TracingExecutionInterceptor;
import io.opentelemetry.instrumentation.awssdk.v2_2.internal.TracingList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import org.springframework.messaging.Message;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

public class SpringAwsUtil {
  private static final ThreadLocal<TracingList> context = new ThreadLocal<>();
  private static final VirtualField<Message<?>, TracingContext> tracingContextField =
      VirtualField.find(Message.class, TracingContext.class);

  private static final MessagingAttributesGetter<Collection<Message<?>>, Void>
      BATCH_ATTRIBUTES_GETTER =
          new MessagingAttributesGetter<Collection<Message<?>>, Void>() {
            @Override
            public String getSystem(Collection<Message<?>> messages) {
              return "aws_sqs";
            }

            @Override
            public String getDestination(Collection<Message<?>> messages) {
              if (!messages.isEmpty()) {
                Message<?> message = messages.iterator().next();
                TracingContext tracingContext = tracingContextField.get(message);
                if (tracingContext != null) {
                  SdkRequest sdkRequest =
                      tracingContext.request.getAttribute(
                          TracingExecutionInterceptor.SDK_REQUEST_ATTRIBUTE);
                  if (sdkRequest instanceof ReceiveMessageRequest) {
                    String queueUrl = ((ReceiveMessageRequest) sdkRequest).queueUrl();
                    if (queueUrl != null) {
                      int i = queueUrl.lastIndexOf('/');
                      if (i > 0) {
                        return queueUrl.substring(i + 1);
                      }
                    }
                  }
                }
              }
              return null;
            }

            @Nullable
            @Override
            public String getDestinationTemplate(Collection<Message<?>> messages) {
              return null;
            }

            @Override
            public boolean isTemporaryDestination(Collection<Message<?>> messages) {
              return false;
            }

            @Override
            public boolean isAnonymousDestination(Collection<Message<?>> messages) {
              return false;
            }

            @Nullable
            @Override
            public String getConversationId(Collection<Message<?>> messages) {
              return null;
            }

            @Nullable
            @Override
            public Long getMessageBodySize(Collection<Message<?>> messages) {
              return null;
            }

            @Nullable
            @Override
            public Long getMessageEnvelopeSize(Collection<Message<?>> messages) {
              return null;
            }

            @Nullable
            @Override
            public String getMessageId(Collection<Message<?>> messages, @Nullable Void unused) {
              return null;
            }

            @Nullable
            @Override
            public String getClientId(Collection<Message<?>> messages) {
              return null;
            }

            @Nullable
            @Override
            public Long getBatchMessageCount(
                Collection<Message<?>> messages, @Nullable Void unused) {
              return (long) messages.size();
            }

            @Override
            public List<String> getMessageHeader(Collection<Message<?>> messages, String name) {
              return emptyList();
            }
          };

  private static final Instrumenter<Collection<Message<?>>, Void> BATCH_INSTRUMENTER =
      Instrumenter.<Collection<Message<?>>, Void>builder(
              GlobalOpenTelemetry.get(),
              "io.opentelemetry.spring-cloud-aws-3.0",
              MessagingSpanNameExtractor.create(BATCH_ATTRIBUTES_GETTER, MessageOperation.PROCESS))
          .addAttributesExtractor(
              MessagingAttributesExtractor.builder(
                      BATCH_ATTRIBUTES_GETTER, MessageOperation.PROCESS)
                  .build())
          .addSpanLinksExtractor(
              (spanLinks, parentContext, messages) -> {
                for (Message<?> message : messages) {
                  TracingContext tracingContext = tracingContextField.get(message);
                  if (tracingContext != null) {
                    SqsMessage wrappedMessage = SqsMessageImpl.wrap(tracingContext.sqsMessage);
                    Context extracted =
                        SqsParentContext.ofMessage(wrappedMessage, tracingContext.config);
                    spanLinks.addLink(Span.fromContext(extracted).getSpanContext());
                  }
                }
              })
          .buildInstrumenter(SpanKindExtractor.alwaysConsumer());

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

    // Start a separate trace (NO parent from the queue) using Context.current()
    // The instrumenter adds span links to all messages.
    Context parentContext = Context.current();
    if (!BATCH_INSTRUMENTER.shouldStart(parentContext, messages)) {
      return null;
    }
    Context context = BATCH_INSTRUMENTER.start(parentContext, messages);

    for (Message<?> msg : messages) {
      TracingContext tc = tracingContextField.get(msg);
      if (tc != null) {
        tc.batchProcessContext = context;
      }
    }

    return new BatchMessageScope(context, messages);
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
    private final Context context;
    private final Collection<Message<?>> request;
    private final Scope scope;

    private BatchMessageScope(Context context, Collection<Message<?>> request) {
      this.context = context;
      this.request = request;
      this.scope = context.makeCurrent();
    }

    public void close(@Nullable Throwable throwable) {
      scope.close();
      BATCH_INSTRUMENTER.end(context, request, null, throwable);
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

  private static class TracingContext {
    private final ExecutionAttributes request;
    private final Response response;
    private final Instrumenter<SqsProcessRequest, Response> instrumenter;
    private final TracingExecutionInterceptor config;
    @Nullable private final Context receiveContext;
    private final software.amazon.awssdk.services.sqs.model.Message sqsMessage;
    @Nullable Context batchProcessContext;

    private TracingContext(
        TracingList tracingList, software.amazon.awssdk.services.sqs.model.Message sqsMessage) {
      this.request = tracingList.getRequest();
      this.response = tracingList.getResponse();
      this.instrumenter = tracingList.getInstrumenter();
      this.config = tracingList.getConfig();
      this.receiveContext = tracingList.getReceiveContext();
      this.sqsMessage = sqsMessage;
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
      return new MessageScope(instrumenter, context, processRequest, response);
    }
  }

  private SpringAwsUtil() {}
}

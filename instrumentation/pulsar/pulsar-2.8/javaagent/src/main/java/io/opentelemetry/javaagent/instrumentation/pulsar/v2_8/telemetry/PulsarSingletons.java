/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingExceptionEventExtractors.setMessagingProcessExceptionEventExtractor;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingExceptionEventExtractors.setMessagingReceiveExceptionEventExtractor;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingExceptionEventExtractors.setMessagingSendExceptionEventExtractor;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingConsumerMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingProcessMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingProducerMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanKindExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingProcessInstrumenterFactory;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import io.opentelemetry.instrumentation.api.internal.PropagatorBasedSpanLinksExtractor;
import io.opentelemetry.instrumentation.api.internal.Timer;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.VirtualFieldStore;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.Messages;

public class PulsarSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.pulsar-2.8";

  // messaging.operation.name values, named after the Pulsar API operations
  private static final String SEND_OPERATION_NAME = "send";
  private static final String RECEIVE_OPERATION_NAME = "receive";
  private static final String PROCESS_OPERATION_NAME = "process";

  private static final OpenTelemetry telemetry = GlobalOpenTelemetry.get();
  private static final TextMapPropagator propagator =
      telemetry.getPropagators().getTextMapPropagator();
  private static final IncludeExclude headers = ExperimentalConfig.get().getMessagingHeaders();
  private static final boolean receiveInstrumentationEnabled =
      ExperimentalConfig.get().messagingReceiveInstrumentationEnabled();

  private static final Instrumenter<PulsarRequest, Void> consumerProcessInstrumenter =
      createConsumerProcessInstrumenter();
  private static final Instrumenter<PulsarRequest, Void> consumerReceiveInstrumenter =
      createConsumerReceiveInstrumenter();
  private static final Instrumenter<PulsarBatchRequest, Void> consumerBatchReceiveInstrumenter =
      createConsumerBatchReceiveInstrumenter();
  private static final Instrumenter<PulsarRequest, Void> producerInstrumenter =
      createProducerInstrumenter();

  private static final ThreadLocal<Boolean> suppressReceive = new ThreadLocal<>();

  public static Instrumenter<PulsarRequest, Void> consumerProcessInstrumenter() {
    return consumerProcessInstrumenter;
  }

  public static Instrumenter<PulsarRequest, Void> producerInstrumenter() {
    return producerInstrumenter;
  }

  private static Instrumenter<PulsarRequest, Void> createConsumerReceiveInstrumenter() {
    MessagingAttributesGetter<PulsarRequest, Void> getter = new PulsarMessagingAttributesGetter();

    InstrumenterBuilder<PulsarRequest, Void> instrumenterBuilder =
        Instrumenter.<PulsarRequest, Void>builder(
                telemetry,
                INSTRUMENTATION_NAME,
                MessagingSpanNameExtractor.create(
                    getter, MessagingOperationType.RECEIVE, RECEIVE_OPERATION_NAME))
            .addAttributesExtractor(
                createMessagingAttributesExtractor(
                    getter, MessagingOperationType.RECEIVE, RECEIVE_OPERATION_NAME))
            .addOperationMetrics(MessagingConsumerMetrics.getForOperationTypeWithOldMetrics())
            .addAttributesExtractor(
                ServerAttributesExtractor.create(new PulsarNetClientAttributesGetter()));
    setMessagingReceiveExceptionEventExtractor(instrumenterBuilder);

    if (emitStableMessagingSemconv() || receiveInstrumentationEnabled) {
      return instrumenterBuilder
          .addSpanLinksExtractor(
              new PropagatorBasedSpanLinksExtractor<>(propagator, MessageTextMapGetter.INSTANCE))
          .buildInstrumenter(MessagingSpanKindExtractor.create(MessagingOperationType.RECEIVE));
    }
    return instrumenterBuilder.buildInstrumenter(
        MessagingSpanKindExtractor.create(MessagingOperationType.RECEIVE));
  }

  private static Instrumenter<PulsarBatchRequest, Void> createConsumerBatchReceiveInstrumenter() {
    MessagingAttributesGetter<PulsarBatchRequest, Void> getter =
        new PulsarBatchMessagingAttributesGetter();

    InstrumenterBuilder<PulsarBatchRequest, Void> instrumenterBuilder =
        Instrumenter.<PulsarBatchRequest, Void>builder(
                telemetry,
                INSTRUMENTATION_NAME,
                MessagingSpanNameExtractor.create(
                    getter, MessagingOperationType.RECEIVE, RECEIVE_OPERATION_NAME))
            .addAttributesExtractor(
                createMessagingAttributesExtractor(
                    getter, MessagingOperationType.RECEIVE, RECEIVE_OPERATION_NAME))
            .addAttributesExtractor(
                ServerAttributesExtractor.create(new PulsarNetClientAttributesGetter()))
            .addSpanLinksExtractor(new PulsarBatchRequestSpanLinksExtractor(propagator))
            .addOperationMetrics(MessagingConsumerMetrics.getForOperationTypeWithOldMetrics());
    setMessagingReceiveExceptionEventExtractor(instrumenterBuilder);
    return instrumenterBuilder.buildInstrumenter(
        MessagingSpanKindExtractor.create(MessagingOperationType.RECEIVE));
  }

  private static Instrumenter<PulsarRequest, Void> createConsumerProcessInstrumenter() {
    MessagingAttributesGetter<PulsarRequest, Void> getter = new PulsarMessagingAttributesGetter();

    InstrumenterBuilder<PulsarRequest, Void> instrumenterBuilder =
        Instrumenter.<PulsarRequest, Void>builder(
                telemetry,
                INSTRUMENTATION_NAME,
                MessagingSpanNameExtractor.create(
                    getter, MessagingOperationType.PROCESS, PROCESS_OPERATION_NAME))
            .addAttributesExtractor(
                createMessagingAttributesExtractor(
                    getter, MessagingOperationType.PROCESS, PROCESS_OPERATION_NAME))
            .addOperationMetrics(MessagingProcessMetrics.get());
    if (!receiveInstrumentationEnabled && emitStableMessagingSemconv()) {
      instrumenterBuilder.addOperationMetrics(MessagingConsumerMetrics.getConsumedMessages());
    }
    setMessagingProcessExceptionEventExtractor(instrumenterBuilder);

    return MessagingProcessInstrumenterFactory.create(
        instrumenterBuilder,
        propagator,
        MessageTextMapGetter.INSTANCE,
        receiveInstrumentationEnabled);
  }

  private static Instrumenter<PulsarRequest, Void> createProducerInstrumenter() {
    MessagingAttributesGetter<PulsarRequest, Void> getter = new PulsarMessagingAttributesGetter();

    InstrumenterBuilder<PulsarRequest, Void> builder =
        Instrumenter.<PulsarRequest, Void>builder(
                telemetry,
                INSTRUMENTATION_NAME,
                MessagingSpanNameExtractor.create(
                    getter, MessagingOperationType.SEND, SEND_OPERATION_NAME))
            .addAttributesExtractor(
                createMessagingAttributesExtractor(
                    getter, MessagingOperationType.SEND, SEND_OPERATION_NAME))
            .addAttributesExtractor(
                ServerAttributesExtractor.create(new PulsarNetClientAttributesGetter()))
            .addOperationMetrics(MessagingProducerMetrics.getForOperationTypeWithOldMetrics());

    if (DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "pulsar")
        .getBoolean("experimental_span_attributes/development", false)) {
      builder.addAttributesExtractor(new ExperimentalProducerAttributesExtractor());
    }
    setMessagingSendExceptionEventExtractor(builder);

    return builder.buildProducerInstrumenter(new MessageTextMapSetter());
  }

  private static <T> AttributesExtractor<T, Void> createMessagingAttributesExtractor(
      MessagingAttributesGetter<T, Void> getter,
      MessagingOperationType operationType,
      String operationName) {
    return MessagingAttributesExtractor.builder(getter, operationType, operationName)
        .setHeaders(headers)
        .build();
  }

  @Nullable
  public static Context startAndEndConsumerReceive(
      Context parent,
      @Nullable Message<?> message,
      Timer timer,
      Consumer<?> consumer,
      @Nullable Throwable throwable) {
    if (message == null) {
      return null;
    }
    String brokerUrl = VirtualFieldStore.extract(consumer);
    PulsarRequest request = PulsarRequest.create(message, brokerUrl, consumer);
    if (!receiveInstrumentationEnabled) {
      // suppress receive span when receive telemetry is not enabled and message is going to be
      // processed by a listener
      if (MessageListenerContext.isProcessing()) {
        return null;
      }
      if (!emitStableMessagingSemconv()) {
        parent = propagator.extract(parent, request, MessageTextMapGetter.INSTANCE);
      }
    }
    if (!consumerReceiveInstrumenter.shouldStart(parent, request)) {
      return null;
    }
    Context receiveContext =
        InstrumenterUtil.startAndEnd(
            consumerReceiveInstrumenter,
            parent,
            request,
            null,
            throwable,
            timer.startTime(),
            timer.now());
    Context processParentContext = emitStableMessagingSemconv() ? parent : receiveContext;
    VirtualFieldStore.markReceiveTelemetryRecorded(message);
    // injected context is used in MessageListenerInstrumentation and also in the spring-pulsar
    // instrumentation
    VirtualFieldStore.inject(message, processParentContext);
    return processParentContext;
  }

  @Nullable
  private static Context startAndEndConsumerReceive(
      Context parent,
      Messages<?> messages,
      Timer timer,
      Consumer<?> consumer,
      @Nullable Throwable throwable) {
    if (messages == null || messages.size() == 0) {
      return null;
    }
    String brokerUrl = VirtualFieldStore.extract(consumer);
    PulsarBatchRequest request = PulsarBatchRequest.create(messages, brokerUrl, consumer);
    if (!consumerBatchReceiveInstrumenter.shouldStart(parent, request)) {
      return null;
    }
    Context receiveContext =
        InstrumenterUtil.startAndEnd(
            consumerBatchReceiveInstrumenter,
            parent,
            request,
            null,
            throwable,
            timer.startTime(),
            timer.now());
    Context processParentContext = emitStableMessagingSemconv() ? parent : receiveContext;
    // injected context is used in MessageListenerInstrumentation and also in the spring-pulsar
    // instrumentation
    for (Message<?> message : messages) {
      VirtualFieldStore.markReceiveTelemetryRecorded(message);
      VirtualFieldStore.inject(message, processParentContext);
    }
    return processParentContext;
  }

  public static CompletableFuture<Void> wrap(CompletableFuture<Void> future) {
    Context parent = Context.current();
    CompletableFuture<Void> result = new CompletableFuture<>();
    future.whenComplete(
        (unused, t) ->
            runWithContext(
                parent,
                () -> {
                  if (t != null) {
                    result.completeExceptionally(t);
                  } else {
                    result.complete(null);
                  }
                }));

    return result;
  }

  public static CompletableFuture<Message<?>> wrap(
      CompletableFuture<Message<?>> future, Timer timer, Consumer<?> consumer) {
    if (isSuppressingReceive()) {
      return future;
    }

    boolean listenerContextActive = MessageListenerContext.isProcessing();
    Context parent = Context.current();
    CompletableFuture<Message<?>> result = new CompletableFuture<>();
    future.whenComplete(
        (message, throwable) -> {
          // we create a "receive" span when receive telemetry is enabled or when we know that
          // this message will not be passed to a listener that would create the "process" span
          Context context =
              receiveInstrumentationEnabled || !listenerContextActive
                  ? startAndEndConsumerReceive(parent, message, timer, consumer, throwable)
                  : parent;
          runWithContext(
              context,
              () -> {
                if (throwable != null) {
                  result.completeExceptionally(throwable);
                } else {
                  result.complete(message);
                }
              });
        });

    return result;
  }

  public static CompletableFuture<Messages<?>> wrapBatch(
      CompletableFuture<Messages<?>> future, Timer timer, Consumer<?> consumer) {
    if (isSuppressingReceive()) {
      return future;
    }

    Context parent = Context.current();
    CompletableFuture<Messages<?>> result = new CompletableFuture<>();
    future.whenComplete(
        (messages, throwable) -> {
          Context context =
              startAndEndConsumerReceive(parent, messages, timer, consumer, throwable);
          runWithContext(
              context,
              () -> {
                if (throwable != null) {
                  result.completeExceptionally(throwable);
                } else {
                  result.complete(messages);
                }
              });
        });

    return result;
  }

  private static void runWithContext(@Nullable Context context, Runnable runnable) {
    if (context != null) {
      try (Scope ignored = context.makeCurrent()) {
        runnable.run();
      }
    } else {
      runnable.run();
    }
  }

  public static void startSuppressingReceive() {
    suppressReceive.set(true);
  }

  public static void endSuppressingReceive() {
    suppressReceive.remove();
  }

  private static boolean isSuppressingReceive() {
    return Boolean.TRUE.equals(suppressReceive.get());
  }

  private PulsarSingletons() {}
}

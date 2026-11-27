/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0;

import static io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0.VertxEventBusSingletons.publishInstrumenter;
import static io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0.VertxEventBusSingletons.sendInstrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.vertx.core.MultiMap;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageProducer;
import javax.annotation.Nullable;

/**
 * A producer operation: {@code send}, {@code publish} or {@code request}, on the event bus or on a
 * {@code MessageProducer}. The producer context is current during the operation, which makes {@link
 * CreateMessageInstrumentation} inject it and local deliveries continue from it.
 */
public final class ProducerScope {

  private static final ThreadLocal<ProducerScope> inProgress = new ThreadLocal<>();

  private final CallDepth callDepth;
  @Nullable private final Instrumenter<EventBusRequest, Void> instrumenter;
  @Nullable private final EventBusRequest request;
  @Nullable private final Context parentContext;
  @Nullable private final Context context;
  @Nullable private final Scope scope;

  private ProducerScope(CallDepth callDepth) {
    this(callDepth, null, null, null, null, null);
  }

  private ProducerScope(
      CallDepth callDepth,
      @Nullable Instrumenter<EventBusRequest, Void> instrumenter,
      @Nullable EventBusRequest request,
      @Nullable Context parentContext,
      @Nullable Context context,
      @Nullable Scope scope) {
    this.callDepth = callDepth;
    this.instrumenter = instrumenter;
    this.request = request;
    this.parentContext = parentContext;
    this.context = context;
    this.scope = scope;
  }

  public static ProducerScope start(
      Instrumenter<EventBusRequest, Void> instrumenter,
      EventBus eventBus,
      @Nullable String address,
      @Nullable Object options) {
    CallDepth callDepth = CallDepth.forClass(EventBus.class);
    // send/publish/request overloads delegate to each other, only the outermost is traced
    if (callDepth.getAndIncrement() > 0
        || address == null
        || EventBusInternals.isInternalAddress(address)
        || NativeTracing.isEnabled(eventBus)) {
      return new ProducerScope(callDepth);
    }
    return start(callDepth, instrumenter, address, options);
  }

  /**
   * Starts an operation on a {@code MessageProducer}. The instrumenter is null when the operation
   * depends on whether the producer sends or publishes.
   */
  public static ProducerScope start(
      @Nullable Instrumenter<EventBusRequest, Void> instrumenter,
      MessageProducer<?> producer,
      @Nullable Object options) {
    // a MessageProducer delegates to the event bus in Vert.x 3.0, which is then nested
    CallDepth callDepth = CallDepth.forClass(EventBus.class);
    if (callDepth.getAndIncrement() > 0) {
      return new ProducerScope(callDepth);
    }
    MessageProducerState state = MessageProducerState.get(producer);
    String address = producer.address();
    if (state == null
        || state.isNativeTracing()
        || address == null
        || EventBusInternals.isInternalAddress(address)) {
      return new ProducerScope(callDepth);
    }
    if (instrumenter == null) {
      instrumenter = state.isPublish() ? publishInstrumenter() : sendInstrumenter();
    }
    return start(
        callDepth,
        instrumenter,
        address,
        options instanceof DeliveryOptions ? options : state.getOptions());
  }

  private static ProducerScope start(
      CallDepth callDepth,
      Instrumenter<EventBusRequest, Void> instrumenter,
      String address,
      @Nullable Object options) {
    MultiMap headers =
        options instanceof DeliveryOptions ? ((DeliveryOptions) options).getHeaders() : null;
    EventBusRequest request = EventBusRequest.forProducer(address, headers);
    Context parentContext = Context.current();
    if (!instrumenter.shouldStart(parentContext, request)) {
      return new ProducerScope(callDepth);
    }
    Context context = instrumenter.start(parentContext, request);
    ProducerScope producerScope =
        new ProducerScope(
            callDepth, instrumenter, request, parentContext, context, context.makeCurrent());
    inProgress.set(producerScope);
    return producerScope;
  }

  /**
   * The context of the caller of the producer operation in progress on this thread, or else the
   * current context. Reply failures are handled in it, like the callbacks of other client
   * operations.
   */
  static Context callerContext() {
    Context current = Context.current();
    ProducerScope producerScope = inProgress.get();
    if (producerScope != null
        && producerScope.context == current
        && producerScope.parentContext != null) {
      return producerScope.parentContext;
    }
    return current;
  }

  public void end(@Nullable Throwable throwable) {
    if (callDepth.decrementAndGet() > 0
        || instrumenter == null
        || request == null
        || context == null
        || scope == null) {
      return;
    }
    scope.close();
    inProgress.remove();
    instrumenter.end(context, request, null, throwable);
  }
}

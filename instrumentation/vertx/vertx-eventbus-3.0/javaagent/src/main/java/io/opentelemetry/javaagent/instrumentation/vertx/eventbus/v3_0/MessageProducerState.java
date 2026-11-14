/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageProducer;
import javax.annotation.Nullable;

/** What a {@code MessageProducer} was created with, which its public API doesn't expose. */
final class MessageProducerState {

  private static final VirtualField<MessageProducer<?>, MessageProducerState> STATE =
      VirtualField.find(MessageProducer.class, MessageProducerState.class);

  private final boolean publish;
  private final boolean nativeTracing;
  @Nullable private volatile DeliveryOptions options;

  private MessageProducerState(
      boolean publish, boolean nativeTracing, @Nullable DeliveryOptions options) {
    this.publish = publish;
    this.nativeTracing = nativeTracing;
    this.options = options;
  }

  /** The owner is the event bus the producer sends with in Vert.x 3.0, the Vertx after that. */
  static void create(
      MessageProducer<?> producer, Object owner, boolean send, @Nullable Object options) {
    boolean nativeTracing =
        owner instanceof Vertx
            ? NativeTracing.isEnabled((Vertx) owner)
            : owner instanceof EventBus && NativeTracing.isEnabled((EventBus) owner);
    STATE.set(
        producer,
        new MessageProducerState(
            !send,
            nativeTracing,
            options instanceof DeliveryOptions ? (DeliveryOptions) options : null));
  }

  @Nullable
  static MessageProducerState get(MessageProducer<?> producer) {
    return STATE.get(producer);
  }

  boolean isPublish() {
    return publish;
  }

  boolean isNativeTracing() {
    return nativeTracing;
  }

  @Nullable
  DeliveryOptions getOptions() {
    return options;
  }

  void setOptions(@Nullable DeliveryOptions options) {
    this.options = options;
  }
}

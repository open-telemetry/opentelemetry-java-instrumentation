/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.integration.v4_1.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.internal.ScopedThreadValue;
import javax.annotation.Nullable;
import org.springframework.messaging.Message;

/**
 * Internal state for a message-producing endpoint's immediate channel handoff.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class SpringIntegrationHandoff {

  private static final String AMQP_INBOUND_CHANNEL_ADAPTER =
      "org.springframework.integration.amqp.inbound.AmqpInboundChannelAdapter";

  private static final ScopedThreadValue<Context> currentLowerProcessing =
      new ScopedThreadValue<>();
  private static final ScopedThreadValue<Message<?>> currentMessage = new ScopedThreadValue<>();

  @Nullable
  public static Object enter(Object endpoint, Message<?> message) {
    if (currentLowerProcessing.get() == null || !isAmqpInboundChannelAdapter(endpoint.getClass())) {
      return null;
    }
    return new MessageState(currentMessage.set(message));
  }

  public static void exit(@Nullable Object state) {
    if (state != null) {
      currentMessage.restore(((MessageState) state).previous);
    }
  }

  @Nullable
  public static Object enterLowerProcessing(@Nullable Context context) {
    return new ProcessingState(currentLowerProcessing.set(context));
  }

  public static void exitLowerProcessing(@Nullable Object state) {
    if (state != null) {
      currentLowerProcessing.restore(((ProcessingState) state).previous);
    }
  }

  public static boolean isCurrent(Message<?> message) {
    return currentMessage.get() == message;
  }

  @Nullable
  public static Context currentLowerProcessing() {
    return currentLowerProcessing.get();
  }

  public static void forward(Message<?> inputMessage, Message<?> outputMessage) {
    if (isCurrent(inputMessage)) {
      currentMessage.set(outputMessage);
    }
  }

  private static boolean isAmqpInboundChannelAdapter(Class<?> type) {
    do {
      if (type.getName().equals(AMQP_INBOUND_CHANNEL_ADAPTER)) {
        return true;
      }
      type = type.getSuperclass();
    } while (type != null);
    return false;
  }

  private SpringIntegrationHandoff() {}

  private static final class MessageState {
    @Nullable private final Message<?> previous;

    private MessageState(@Nullable Message<?> previous) {
      this.previous = previous;
    }
  }

  private static final class ProcessingState {
    @Nullable private final Context previous;

    private ProcessingState(@Nullable Context previous) {
      this.previous = previous;
    }
  }
}

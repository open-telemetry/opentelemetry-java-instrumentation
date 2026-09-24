/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.integration.v4_1;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.spring.integration.v4_1.internal.SpringIntegrationHandoff;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import javax.annotation.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

final class MessageInvocation {

  // This identifies local Spring Integration work without serializing ownership into message
  // headers.
  private static final ContextKey<Identity> CURRENT_INVOCATION =
      ContextKey.named("opentelemetry-spring-integration-current-invocation");

  private static final ThreadLocal<Deque<MessageInvocation>> currentInvocations =
      new ThreadLocal<>();

  private static final ThreadLocal<Deque<Callback>> currentCallbacks = new ThreadLocal<>();

  private final Identity identity;
  private final MessageWithChannel request;
  @Nullable private final Context telemetryContext;
  @Nullable private final Instrumenter<MessageWithChannel, Void> instrumenter;
  private final Scope scope;

  private MessageInvocation(
      Identity identity,
      MessageWithChannel request,
      Context scopedContext,
      @Nullable Context telemetryContext,
      @Nullable Instrumenter<MessageWithChannel, Void> instrumenter) {
    this.identity = identity;
    this.request = request;
    this.telemetryContext = telemetryContext;
    this.instrumenter = instrumenter;
    scope = scopedContext.with(CURRENT_INVOCATION, identity).makeCurrent();
  }

  static void start(
      Message<?> inputMessage,
      Message<?> outputMessage,
      MessageChannel channel,
      @Nullable MessageHandler handler,
      MessageWithChannel request,
      Context scopedContext,
      @Nullable Context telemetryContext,
      @Nullable Instrumenter<MessageWithChannel, Void> instrumenter) {
    Identity identity =
        new Identity(
            inputMessage,
            outputMessage,
            channel,
            handler,
            SpringIntegrationHandoff.currentLowerProcessing());
    MessageInvocation invocation =
        new MessageInvocation(identity, request, scopedContext, telemetryContext, instrumenter);
    Deque<MessageInvocation> invocations = currentInvocations.get();
    if (invocations == null) {
      invocations = new ArrayDeque<>();
      currentInvocations.set(invocations);
    }
    invocations.addLast(invocation);
    addCallback(new Callback(channel, handler, handler != null, invocation));
  }

  static boolean enterDuplicateSend(
      Message<?> message, MessageChannel channel, int interceptorCount) {
    return enterDuplicate(message, channel, null, false, interceptorCount);
  }

  static boolean enterDuplicateHandler(
      Message<?> message, MessageChannel channel, MessageHandler handler, int interceptorCount) {
    return enterDuplicate(message, channel, handler, true, interceptorCount);
  }

  static void startNoopSend(MessageChannel channel) {
    addCallback(new Callback(channel, null, false, null));
  }

  static boolean currentIsSendFor(Context context, Message<?> message, MessageChannel channel) {
    Identity identity = context.get(CURRENT_INVOCATION);
    return identity != null && identity.matches(message, channel, null, false);
  }

  static boolean currentUsesLowerProcessing(Context context, @Nullable Context lowerProcessing) {
    Identity identity = context.get(CURRENT_INVOCATION);
    return identity != null && identity.lowerProcessing == lowerProcessing;
  }

  static void endSend(MessageChannel channel, @Nullable Throwable throwable) {
    end(channel, null, false, throwable);
  }

  static void endHandler(
      MessageChannel channel, MessageHandler handler, @Nullable Throwable throwable) {
    end(channel, handler, true, throwable);
  }

  private static void end(
      MessageChannel channel,
      @Nullable MessageHandler handler,
      boolean handlerInvocation,
      @Nullable Throwable throwable) {
    Deque<Callback> callbacks = currentCallbacks.get();
    if (callbacks == null) {
      return;
    }
    Callback callback = findCompletion(callbacks, channel, handler, handlerInvocation);
    if (callback == null) {
      return;
    }

    callbacks.remove(callback);
    if (callbacks.isEmpty()) {
      currentCallbacks.remove();
    }

    MessageInvocation invocation = callback.invocation;
    if (invocation == null) {
      return;
    }

    Deque<MessageInvocation> invocations = currentInvocations.get();
    if (invocations == null) {
      return;
    }
    invocations.remove(invocation);
    if (invocations.isEmpty()) {
      currentInvocations.remove();
    }
    invocation.complete(throwable);
  }

  private static boolean enterDuplicate(
      Message<?> message,
      MessageChannel channel,
      @Nullable MessageHandler handler,
      boolean handlerInvocation,
      int interceptorCount) {
    MessageInvocation invocation = find(message, channel, handler, handlerInvocation);
    Deque<Callback> callbacks = currentCallbacks.get();
    if (invocation == null
        || callbacks == null
        || callbacks.isEmpty()
        || callbacks.peekLast().owner != invocation
        || countCallbacks(callbacks, invocation) >= interceptorCount) {
      return false;
    }
    addCallback(new Callback(channel, handler, handlerInvocation, null, invocation));
    return true;
  }

  private static int countCallbacks(Deque<Callback> callbacks, MessageInvocation invocation) {
    int count = 0;
    for (Callback callback : callbacks) {
      if (callback.owner == invocation) {
        count++;
      }
    }
    return count;
  }

  @Nullable
  private static MessageInvocation find(
      Message<?> message,
      MessageChannel channel,
      @Nullable MessageHandler handler,
      boolean handlerInvocation) {
    Deque<MessageInvocation> invocations = currentInvocations.get();
    return invocations == null
        ? null
        : find(invocations, message, channel, handler, handlerInvocation);
  }

  @Nullable
  private static MessageInvocation find(
      Deque<MessageInvocation> invocations,
      Message<?> message,
      MessageChannel channel,
      @Nullable MessageHandler handler,
      boolean handlerInvocation) {
    Iterator<MessageInvocation> iterator = invocations.descendingIterator();
    while (iterator.hasNext()) {
      MessageInvocation invocation = iterator.next();
      if (invocation.identity.matches(message, channel, handler, handlerInvocation)) {
        return invocation;
      }
    }
    return null;
  }

  @Nullable
  private static Callback findCompletion(
      Deque<Callback> callbacks,
      MessageChannel channel,
      @Nullable MessageHandler handler,
      boolean handlerInvocation) {
    Iterator<Callback> iterator = callbacks.descendingIterator();
    while (iterator.hasNext()) {
      Callback callback = iterator.next();
      if (callback.matches(channel, handler, handlerInvocation)) {
        return callback;
      }
    }
    return null;
  }

  private static void addCallback(Callback callback) {
    Deque<Callback> callbacks = currentCallbacks.get();
    if (callbacks == null) {
      callbacks = new ArrayDeque<>();
      currentCallbacks.set(callbacks);
    }
    callbacks.addLast(callback);
  }

  private void complete(@Nullable Throwable throwable) {
    scope.close();
    if (telemetryContext != null && instrumenter != null) {
      instrumenter.end(telemetryContext, request, null, throwable);
    }
  }

  private static final class Callback {
    private final MessageChannel channel;
    @Nullable private final MessageHandler handler;
    private final boolean handlerInvocation;
    @Nullable private final MessageInvocation invocation;
    @Nullable private final MessageInvocation owner;

    private Callback(
        MessageChannel channel,
        @Nullable MessageHandler handler,
        boolean handlerInvocation,
        @Nullable MessageInvocation invocation) {
      this(channel, handler, handlerInvocation, invocation, invocation);
    }

    private Callback(
        MessageChannel channel,
        @Nullable MessageHandler handler,
        boolean handlerInvocation,
        @Nullable MessageInvocation invocation,
        @Nullable MessageInvocation owner) {
      this.channel = channel;
      this.handler = handler;
      this.handlerInvocation = handlerInvocation;
      this.invocation = invocation;
      this.owner = owner;
    }

    private boolean matches(
        MessageChannel channel, @Nullable MessageHandler handler, boolean handlerInvocation) {
      return this.channel == channel
          && this.handlerInvocation == handlerInvocation
          && (!handlerInvocation || this.handler == handler);
    }
  }

  private static final class Identity {
    private final Message<?> inputMessage;
    private final Message<?> outputMessage;
    private final MessageChannel channel;
    @Nullable private final MessageHandler handler;
    @Nullable private final Context lowerProcessing;

    private Identity(
        Message<?> inputMessage,
        Message<?> outputMessage,
        MessageChannel channel,
        @Nullable MessageHandler handler,
        @Nullable Context lowerProcessing) {
      this.inputMessage = inputMessage;
      this.outputMessage = outputMessage;
      this.channel = channel;
      this.handler = handler;
      this.lowerProcessing = lowerProcessing;
    }

    private boolean matches(
        Message<?> message,
        MessageChannel channel,
        @Nullable MessageHandler handler,
        boolean handlerInvocation) {
      return matches(channel, handler, handlerInvocation)
          && (message == inputMessage || message == outputMessage);
    }

    private boolean matches(
        MessageChannel channel, @Nullable MessageHandler handler, boolean handlerInvocation) {
      if (this.channel != channel || (this.handler != null) != handlerInvocation) {
        return false;
      }
      return !handlerInvocation || this.handler == handler;
    }
  }
}

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

// Tracks nested sends and handler invocations independently, including when telemetry is
// suppressed.
final class MessageInvocation {

  // This identifies local Spring Integration work without serializing ownership into message
  // headers.
  private static final ContextKey<Identity> CURRENT_INVOCATION =
      ContextKey.named("opentelemetry-spring-integration-current-invocation");

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
      Message<?> outputMessage,
      @Nullable MessageHandler handler,
      MessageWithChannel request,
      Context scopedContext,
      @Nullable Context telemetryContext,
      @Nullable Instrumenter<MessageWithChannel, Void> instrumenter) {
    Identity identity =
        new Identity(
            request.getMessage(),
            outputMessage,
            request.getMessageChannel(),
            handler,
            SpringIntegrationHandoff.currentLowerProcessing());
    MessageInvocation invocation =
        new MessageInvocation(identity, request, scopedContext, telemetryContext, instrumenter);
    addCallback(new Callback(request.getMessageChannel(), handler, invocation));
  }

  static boolean enterDuplicateSend(
      Message<?> message, MessageChannel channel, int interceptorCount) {
    return enterDuplicate(message, channel, null, interceptorCount);
  }

  static boolean enterDuplicateHandler(
      Message<?> message, MessageChannel channel, MessageHandler handler, int interceptorCount) {
    return enterDuplicate(message, channel, handler, interceptorCount);
  }

  static void startNoopSend(MessageChannel channel) {
    // Propagation-only sends still need an entry so their completion cannot finish an outer send.
    addCallback(new Callback(channel, null, null));
  }

  static boolean currentIsSendFor(Context context, Message<?> message, MessageChannel channel) {
    Identity identity = context.get(CURRENT_INVOCATION);
    return identity != null && identity.matches(message, channel, null);
  }

  static boolean currentUsesLowerProcessing(Context context, @Nullable Context lowerProcessing) {
    Identity identity = context.get(CURRENT_INVOCATION);
    return identity != null && identity.lowerProcessing == lowerProcessing;
  }

  static void endSend(MessageChannel channel, @Nullable Throwable throwable) {
    end(channel, null, throwable);
  }

  static void endHandler(
      MessageChannel channel, MessageHandler handler, @Nullable Throwable throwable) {
    end(channel, handler, throwable);
  }

  private static void end(
      MessageChannel channel, @Nullable MessageHandler handler, @Nullable Throwable throwable) {
    Deque<Callback> callbacks = currentCallbacks.get();
    if (callbacks == null) {
      return;
    }
    Callback callback = findCompletion(callbacks, channel, handler);
    if (callback == null) {
      return;
    }
    callback.remainingCompletions--;
    if (callback.remainingCompletions > 0) {
      return;
    }

    callbacks.remove(callback);
    if (callbacks.isEmpty()) {
      currentCallbacks.remove();
    }

    if (callback.invocation != null) {
      callback.invocation.complete(throwable);
    }
  }

  private static boolean enterDuplicate(
      Message<?> message,
      MessageChannel channel,
      @Nullable MessageHandler handler,
      int interceptorCount) {
    Deque<Callback> callbacks = currentCallbacks.get();
    if (callbacks == null || callbacks.isEmpty()) {
      return false;
    }
    Callback callback = callbacks.peekLast();
    if (callback.invocation == null
        || !callback.invocation.identity.matches(message, channel, handler)
        || callback.remainingCompletions >= interceptorCount) {
      return false;
    }

    // Each registration gets a completion callback. Once all registrations have entered, another
    // entry is a nested invocation, even if it uses the same message, channel, and handler.
    callback.remainingCompletions++;
    return true;
  }

  @Nullable
  private static Callback findCompletion(
      Deque<Callback> callbacks, MessageChannel channel, @Nullable MessageHandler handler) {
    Iterator<Callback> iterator = callbacks.descendingIterator();
    while (iterator.hasNext()) {
      Callback callback = iterator.next();
      if (callback.channel == channel && callback.handler == handler) {
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
    @Nullable private final MessageInvocation invocation;
    private int remainingCompletions = 1;

    private Callback(
        MessageChannel channel,
        @Nullable MessageHandler handler,
        @Nullable MessageInvocation invocation) {
      this.channel = channel;
      this.handler = handler;
      this.invocation = invocation;
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
        Message<?> message, MessageChannel channel, @Nullable MessageHandler handler) {
      return this.channel == channel
          && this.handler == handler
          && (message == inputMessage || message == outputMessage);
    }
  }
}

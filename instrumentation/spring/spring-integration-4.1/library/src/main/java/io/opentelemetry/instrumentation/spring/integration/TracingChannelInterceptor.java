/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.integration;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.List;
import java.util.Map;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.support.NativeMessageHeaderAccessor;
import org.springframework.util.LinkedMultiValueMap;

final class TracingChannelInterceptor implements ExecutorChannelInterceptor {
  private static final String CONTEXT_AND_SCOPE_KEY = ContextAndScope.class.getName();
  private static final String SCOPE_KEY = TracingChannelInterceptor.class.getName() + ".scope";

  private final ContextPropagators propagators;
  private final Instrumenter<MessageWithChannel, Void> instrumenter;

  TracingChannelInterceptor(
      ContextPropagators propagators, Instrumenter<MessageWithChannel, Void> instrumenter) {
    this.propagators = propagators;
    this.instrumenter = instrumenter;
  }

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel messageChannel) {
    Context parentContext = Context.current();
    MessageWithChannel messageWithChannel = MessageWithChannel.create(message, messageChannel);

    final Context context;
    MessageHeaderAccessor messageHeaderAccessor = createMutableHeaderAccessor(message);

    // when there's no CONSUMER span created by another instrumentation, start it; there's no other
    // messaging instrumentation that can do this, so spring-integration must ensure proper context
    // propagation
    // the new CONSUMER span will use the span context extracted from the incoming message as the
    // parent
    if (instrumenter.shouldStart(parentContext, messageWithChannel)) {
      context = instrumenter.start(parentContext, messageWithChannel);
      messageHeaderAccessor.setHeader(
          CONTEXT_AND_SCOPE_KEY, ContextAndScope.create(context, context.makeCurrent()));
    } else {
      // if there was a top-level span detected it means that there's another messaging
      // instrumentation that creates CONSUMER/PRODUCER spans; in that case, back off and just
      // inject the current context into the message
      context = parentContext;
      messageHeaderAccessor.setHeader(
          CONTEXT_AND_SCOPE_KEY, ContextAndScope.create(null, context.makeCurrent()));
    }

    propagators
        .getTextMapPropagator()
        .inject(context, messageHeaderAccessor, MessageHeadersSetter.INSTANCE);
    return createMessageWithHeaders(message, messageHeaderAccessor);
  }

  @Override
  public void postSend(Message<?> message, MessageChannel messageChannel, boolean sent) {}

  @Override
  public void afterSendCompletion(
      Message<?> message, MessageChannel messageChannel, boolean sent, Exception e) {
    Object contextAndScope = message.getHeaders().get(CONTEXT_AND_SCOPE_KEY);
    if (contextAndScope instanceof ContextAndScope) {
      ContextAndScope cas = (ContextAndScope) contextAndScope;
      cas.close();
      Context context = cas.getContext();

      if (context != null) {
        MessageWithChannel messageWithChannel = MessageWithChannel.create(message, messageChannel);
        instrumenter.end(context, messageWithChannel, null, e);
      }
    }
  }

  @Override
  public boolean preReceive(MessageChannel messageChannel) {
    return true;
  }

  @Override
  public Message<?> postReceive(Message<?> message, MessageChannel messageChannel) {
    return message;
  }

  @Override
  public void afterReceiveCompletion(
      Message<?> message, MessageChannel messageChannel, Exception e) {}

  @Override
  public Message<?> beforeHandle(
      Message<?> message, MessageChannel channel, MessageHandler handler) {
    MessageWithChannel messageWithChannel = MessageWithChannel.create(message, channel);
    Context context =
        propagators
            .getTextMapPropagator()
            .extract(Context.current(), messageWithChannel, MessageHeadersGetter.INSTANCE);
    MessageHeaderAccessor messageHeaderAccessor = MessageHeaderAccessor.getMutableAccessor(message);
    messageHeaderAccessor.setHeader(SCOPE_KEY, context.makeCurrent());
    return createMessageWithHeaders(message, messageHeaderAccessor);
  }

  @Override
  public void afterMessageHandled(
      Message<?> message, MessageChannel channel, MessageHandler handler, Exception ex) {
    Object scope = message.getHeaders().get(SCOPE_KEY);
    if (scope instanceof Scope) {
      ((Scope) scope).close();
    }
  }

  private static MessageHeaderAccessor createMutableHeaderAccessor(Message<?> message) {
    MessageHeaderAccessor headerAccessor = MessageHeaderAccessor.getMutableAccessor(message);
    headerAccessor.setLeaveMutable(true);
    ensureNativeHeadersAreMutable(headerAccessor);
    return headerAccessor;
  }

  private static void ensureNativeHeadersAreMutable(MessageHeaderAccessor headerAccessor) {
    Object nativeMap = headerAccessor.getHeader(NativeMessageHeaderAccessor.NATIVE_HEADERS);
    if (nativeMap != null && !(nativeMap instanceof LinkedMultiValueMap)) {
      headerAccessor.setHeader(
          NativeMessageHeaderAccessor.NATIVE_HEADERS,
          new LinkedMultiValueMap<>((Map<String, List<String>>) nativeMap));
    }
  }

  private static Message<?> createMessageWithHeaders(
      Message<?> message, MessageHeaderAccessor messageHeaderAccessor) {
    return MessageBuilder.fromMessage(message)
        .copyHeaders(messageHeaderAccessor.toMessageHeaders())
        .build();
  }
}

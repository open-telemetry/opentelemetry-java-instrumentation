/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.integration;

import io.opentelemetry.api.trace.Span;
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
  // some messaging products (e.g. IBM MQ) require message keys to be valid Java identifiers
  private static final String CONTEXT_AND_SCOPE_KEY =
      ContextAndScope.class.getName().replace('.', '_');
  private static final String SCOPE_KEY =
      TracingChannelInterceptor.class.getName().replace('.', '_') + "_scope";

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

    // only start a new CONSUMER span when there is no span in the context: this situation happens
    // when there's no other messaging instrumentation that can do this - this way
    // spring-integration instrumentation ensures proper context propagation: the new CONSUMER span
    // will use the span context extracted from the incoming message as the parent
    //
    // when there already is a span in the context then it usually means one of two things:
    // 1. spring-integration is a part of the producer invocation, e.g. invoked from a server method
    //    that puts something into a messaging queue/system
    // 2. another messaging instrumentation has already created a CONSUMER span, in which case this
    //    instrumentation should not create another one
    if (shouldStart(parentContext, messageWithChannel)) {
      context = instrumenter.start(parentContext, messageWithChannel);
      messageHeaderAccessor.setHeader(
          CONTEXT_AND_SCOPE_KEY, ContextAndScope.create(context, context.makeCurrent()));
    } else {
      // in case there already was another span in the context: back off and just inject the current
      // context into the message
      context = parentContext;
      messageHeaderAccessor.setHeader(
          CONTEXT_AND_SCOPE_KEY, ContextAndScope.create(null, context.makeCurrent()));
    }

    propagators
        .getTextMapPropagator()
        .inject(context, messageHeaderAccessor, MessageHeadersSetter.INSTANCE);
    return createMessageWithHeaders(message, messageHeaderAccessor);
  }

  private boolean shouldStart(Context parentContext, MessageWithChannel messageWithChannel) {
    return instrumenter.shouldStart(parentContext, messageWithChannel)
        && Span.fromContextOrNull(parentContext) == null;
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

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.integration;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.IdentityHashMap;
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

  private static final ThreadLocal<Map<MessageChannel, ContextAndScope>> LOCAL_CONTEXT_AND_SCOPE =
      ThreadLocal.withInitial(IdentityHashMap::new);

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

    if (!instrumenter.shouldStart(parentContext, messageWithChannel)) {
      return message;
    }
    Context context = instrumenter.start(parentContext, messageWithChannel);

    MessageHeaderAccessor messageHeaderAccessor = createMutableHeaderAccessor(message);
    propagators
        .getTextMapPropagator()
        .inject(context, messageHeaderAccessor, MessageHeadersSetter.INSTANCE);
    LOCAL_CONTEXT_AND_SCOPE.get().put(messageChannel, ContextAndScope.makeCurrent(context));
    return createMessageWithHeaders(message, messageHeaderAccessor);
  }

  @Override
  public void postSend(Message<?> message, MessageChannel messageChannel, boolean sent) {}

  @Override
  public void afterSendCompletion(
      Message<?> message, MessageChannel messageChannel, boolean sent, Exception e) {
    ContextAndScope contextAndScope = LOCAL_CONTEXT_AND_SCOPE.get().remove(messageChannel);
    if (contextAndScope != null) {
      contextAndScope.close();
      MessageWithChannel messageWithChannel = MessageWithChannel.create(message, messageChannel);
      instrumenter.end(contextAndScope.getContext(), messageWithChannel, null, e);
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
    // beforeHandle()/afterMessageHandles() always execute in a different thread than send(), so
    // there's no real risk of overwriting the send() context
    LOCAL_CONTEXT_AND_SCOPE.get().put(channel, ContextAndScope.makeCurrent(context));
    return message;
  }

  @Override
  public void afterMessageHandled(
      Message<?> message, MessageChannel channel, MessageHandler handler, Exception ex) {
    ContextAndScope contextAndScope = LOCAL_CONTEXT_AND_SCOPE.get().remove(channel);
    if (contextAndScope != null) {
      contextAndScope.close();
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

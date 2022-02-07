/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.integration;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.support.NativeMessageHeaderAccessor;
import org.springframework.util.LinkedMultiValueMap;

final class TracingChannelInterceptor implements ExecutorChannelInterceptor {

  private static final boolean PRODUCER_SPAN_ENABLED =
      Config.get().getBoolean("otel.instrumentation.spring-integration.producer.enabled", false);

  private static final ThreadLocal<Map<MessageChannel, ContextAndScope>> LOCAL_CONTEXT_AND_SCOPE =
      ThreadLocal.withInitial(IdentityHashMap::new);

  private final ContextPropagators propagators;
  private final Instrumenter<MessageWithChannel, Void> consumerInstrumenter;
  private final Instrumenter<MessageWithChannel, Void> producerInstrumenter;

  TracingChannelInterceptor(
      ContextPropagators propagators,
      Instrumenter<MessageWithChannel, Void> consumerInstrumenter,
      Instrumenter<MessageWithChannel, Void> producerInstrumenter) {
    this.propagators = propagators;
    this.consumerInstrumenter = consumerInstrumenter;
    this.producerInstrumenter = producerInstrumenter;
  }

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel messageChannel) {

    Map<MessageChannel, ContextAndScope> localMap = LOCAL_CONTEXT_AND_SCOPE.get();
    if (localMap.get(messageChannel) != null) {
      // GlobalChannelInterceptorProcessor.afterSingletonsInstantiated() adds the global
      // interceptors for every bean name / channel pair, which means it's possible that this
      // interceptor is added twice to the same channel if the channel is registered twice under
      // different bean names
      //
      // there's an option for this class to implement VetoCapableInterceptor and prevent itself
      // from being registered if it's already registered, but the VetoCapableInterceptor interface
      // broke backwards compatibility in 5.2.0, and the version prior to 5.2.0 takes a parameter
      // of type ChannelInterceptorAware which doesn't exist after 5.2.0, and while it's possible to
      // implement both at the same time (since we compile using 4.1.0), muzzle doesn't like the
      // missing class type when running testLatestDeps
      return message;
    }

    boolean createProducerSpan = createProducerSpan(messageChannel);

    Context parentContext = Context.current();
    MessageWithChannel messageWithChannel = MessageWithChannel.create(message, messageChannel);

    Context context;
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
    if (!createProducerSpan && shouldStartConsumer(parentContext, messageWithChannel)) {
      context = consumerInstrumenter.start(parentContext, messageWithChannel);
      localMap.put(messageChannel, ContextAndScope.create(context, context.makeCurrent()));
    } else if (createProducerSpan
        && producerInstrumenter.shouldStart(parentContext, messageWithChannel)) {
      context = producerInstrumenter.start(parentContext, messageWithChannel);
      localMap.put(messageChannel, ContextAndScope.create(context, context.makeCurrent()));
    } else {
      // in case there already was another span in the context: back off and just inject the current
      // context into the message
      context = parentContext;
      localMap.put(messageChannel, ContextAndScope.create(null, context.makeCurrent()));
    }

    propagators
        .getTextMapPropagator()
        .inject(context, messageHeaderAccessor, MessageHeadersSetter.INSTANCE);
    return createMessageWithHeaders(message, messageHeaderAccessor);
  }

  private boolean shouldStartConsumer(
      Context parentContext, MessageWithChannel messageWithChannel) {
    return consumerInstrumenter.shouldStart(parentContext, messageWithChannel)
        && Span.fromContextOrNull(parentContext) == null;
  }

  @Override
  public void postSend(Message<?> message, MessageChannel messageChannel, boolean sent) {}

  @Override
  public void afterSendCompletion(
      Message<?> message, MessageChannel messageChannel, boolean sent, Exception e) {
    ContextAndScope contextAndScope = LOCAL_CONTEXT_AND_SCOPE.get().remove(messageChannel);
    if (contextAndScope != null) {
      contextAndScope.close();
      Context context = contextAndScope.getContext();

      if (context != null) {
        MessageWithChannel messageWithChannel = MessageWithChannel.create(message, messageChannel);
        boolean createProducerSpan = createProducerSpan(messageChannel);
        Instrumenter<MessageWithChannel, Void> instrumenter =
            createProducerSpan ? producerInstrumenter : consumerInstrumenter;
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

    Map<MessageChannel, ContextAndScope> localMap = LOCAL_CONTEXT_AND_SCOPE.get();
    if (localMap.get(channel) != null) {
      // see comment explaining the same conditional in preSend()
      return message;
    }

    MessageWithChannel messageWithChannel = MessageWithChannel.create(message, channel);
    Context context =
        propagators
            .getTextMapPropagator()
            .extract(Context.current(), messageWithChannel, MessageHeadersGetter.INSTANCE);
    // beforeHandle()/afterMessageHandles() always execute in a different thread than send(), so
    // there's no real risk of overwriting the send() context
    localMap.put(channel, ContextAndScope.create(null, context.makeCurrent()));
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
      @SuppressWarnings("unchecked")
      Map<String, List<String>> map = (Map<String, List<String>>) nativeMap;
      headerAccessor.setHeader(
          NativeMessageHeaderAccessor.NATIVE_HEADERS, new LinkedMultiValueMap<>(map));
    }
  }

  private static Message<?> createMessageWithHeaders(
      Message<?> message, MessageHeaderAccessor messageHeaderAccessor) {
    return MessageBuilder.fromMessage(message)
        .copyHeaders(messageHeaderAccessor.toMessageHeaders())
        .build();
  }

  private static final Class<?> directWithAttributesChannelClass =
      getDirectWithAttributesChannelClass();
  private static final MethodHandle channelGetAttributeMh =
      getChannelAttributeMh(directWithAttributesChannelClass);

  private static Class<?> getDirectWithAttributesChannelClass() {
    try {
      return Class.forName(
          "org.springframework.cloud.stream.messaging.DirectWithAttributesChannel");
    } catch (ClassNotFoundException ignore) {
      return null;
    }
  }

  private static MethodHandle getChannelAttributeMh(Class<?> directWithAttributesChannelClass) {
    if (directWithAttributesChannelClass == null) {
      return null;
    }

    try {
      return MethodHandles.lookup()
          .findVirtual(
              directWithAttributesChannelClass,
              "getAttribute",
              MethodType.methodType(Object.class, String.class));
    } catch (NoSuchMethodException | IllegalAccessException exception) {
      return null;
    }
  }

  private static boolean createProducerSpan(MessageChannel messageChannel) {
    if (!PRODUCER_SPAN_ENABLED) {
      return false;
    }

    messageChannel = unwrapProxy(messageChannel);
    if (!directWithAttributesChannelClass.isInstance(messageChannel)) {
      // we can only tell if it is an output channel for instances of DirectWithAttributesChannel
      // that are used by spring cloud stream
      return false;
    }

    try {
      return "output".equals(channelGetAttributeMh.invoke(messageChannel, "type"));
    } catch (Throwable throwable) {
      return false;
    }
  }

  // unwrap spring aop proxy
  // based on org.springframework.test.util.AopTestUtils#getTargetObject
  @SuppressWarnings("unchecked")
  public static <T> T unwrapProxy(T candidate) {
    try {
      if (AopUtils.isAopProxy(candidate) && candidate instanceof Advised) {
        Object target = ((Advised) candidate).getTargetSource().getTarget();
        if (target != null) {
          return (T) target;
        }
      }

      return candidate;
    } catch (Throwable ignore) {
      return candidate;
    }
  }
}

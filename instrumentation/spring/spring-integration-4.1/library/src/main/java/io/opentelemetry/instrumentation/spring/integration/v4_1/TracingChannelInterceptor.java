/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.integration.v4_1;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.spring.integration.v4_1.internal.SpringIntegrationHandoff;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.support.NativeMessageHeaderAccessor;
import org.springframework.util.LinkedMultiValueMap;

final class TracingChannelInterceptor implements ExecutorChannelInterceptor {

  @Nullable
  private static final Class<?> DIRECT_WITH_ATTRIBUTES_CHANNEL_CLASS =
      getDirectWithAttributesChannelClass();

  @Nullable
  private static final Class<?> EXECUTOR_CHANNEL_INTERCEPTOR_AWARE_CLASS =
      getExecutorChannelInterceptorAwareClass();

  @Nullable
  private static final MethodHandle CHANNEL_GET_ATTRIBUTE_MH =
      getChannelAttributeMh(DIRECT_WITH_ATTRIBUTES_CHANNEL_CLASS);

  @Nullable
  private static Class<?> getDirectWithAttributesChannelClass() {
    try {
      return Class.forName(
          "org.springframework.cloud.stream.messaging.DirectWithAttributesChannel");
    } catch (ClassNotFoundException ignored) {
      return null;
    }
  }

  @Nullable
  private static Class<?> getExecutorChannelInterceptorAwareClass() {
    try {
      return Class.forName(
          "org.springframework.integration.channel.ExecutorChannelInterceptorAware");
    } catch (ClassNotFoundException ignored) {
      return null;
    }
  }

  @Nullable
  private static MethodHandle getChannelAttributeMh(
      @Nullable Class<?> directWithAttributesChannelClass) {
    if (directWithAttributesChannelClass == null) {
      return null;
    }

    try {
      return MethodHandles.lookup()
          .findVirtual(
              directWithAttributesChannelClass,
              "getAttribute",
              MethodType.methodType(Object.class, String.class));
    } catch (NoSuchMethodException | IllegalAccessException ignored) {
      return null;
    }
  }

  private final ContextPropagators propagators;
  private final Instrumenter<MessageWithChannel, Void> consumerInstrumenter;
  private final Instrumenter<MessageWithChannel, Void> producerInstrumenter;
  private final boolean producerSpanEnabled;

  TracingChannelInterceptor(
      ContextPropagators propagators,
      Instrumenter<MessageWithChannel, Void> consumerInstrumenter,
      Instrumenter<MessageWithChannel, Void> producerInstrumenter,
      boolean producerSpanEnabled) {
    this.propagators = propagators;
    this.consumerInstrumenter = consumerInstrumenter;
    this.producerInstrumenter = producerInstrumenter;
    this.producerSpanEnabled = producerSpanEnabled;
  }

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel messageChannel) {
    if (MessageInvocation.enterDuplicateSend(message, messageChannel)) {
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

    boolean outputChannel = isSpringCloudStreamOutputChannel(messageChannel);
    Context parentContext = Context.current();
    MessageWithChannel messageWithChannel = MessageWithChannel.create(message, messageChannel);
    Context context = parentContext;
    Context telemetryContext = null;
    Instrumenter<MessageWithChannel, Void> instrumenter = null;
    boolean processingInvocation = false;

    if (outputChannel && producerSpanEnabled) {
      if (producerInstrumenter.shouldStart(parentContext, messageWithChannel)) {
        telemetryContext = producerInstrumenter.start(parentContext, messageWithChannel);
        context = telemetryContext;
        instrumenter = producerInstrumenter;
      }
    } else if (!outputChannel && !supportsHandlerInterception(messageChannel)) {
      processingInvocation = true;
      if (isProcessingSelected(parentContext, message)
          && consumerInstrumenter.shouldStart(parentContext, messageWithChannel)) {
        telemetryContext = startConsumer(parentContext, messageWithChannel);
        context = telemetryContext;
        instrumenter = consumerInstrumenter;
      }
    }

    MessageHeaderAccessor messageHeaderAccessor = createMutableHeaderAccessor(message);
    propagators
        .getTextMapPropagator()
        .inject(context, messageHeaderAccessor, MessageHeadersSetter.INSTANCE);
    Message<?> outputMessage = createMessageWithHeaders(message, messageHeaderAccessor);

    if (instrumenter != null || processingInvocation) {
      MessageInvocation.start(
          message,
          outputMessage,
          messageChannel,
          null,
          messageWithChannel,
          context,
          telemetryContext,
          instrumenter);
    } else {
      MessageInvocation.startNoopSend(messageChannel);
    }
    return outputMessage;
  }

  private static boolean isProcessingSelected(Context currentContext, Message<?> message) {
    return !SpringIntegrationHandoff.isCurrent(message)
        || MessageInvocation.currentUsesLowerProcessing(
            currentContext, SpringIntegrationHandoff.currentLowerProcessing());
  }

  @Override
  public void postSend(Message<?> message, MessageChannel messageChannel, boolean sent) {}

  @Override
  public void afterSendCompletion(
      Message<?> message, MessageChannel messageChannel, boolean sent, Exception e) {
    MessageInvocation.endSend(messageChannel, e);
  }

  @Override
  public boolean preReceive(MessageChannel messageChannel) {
    return true;
  }

  @Override
  @CanIgnoreReturnValue
  public Message<?> postReceive(Message<?> message, MessageChannel messageChannel) {
    return message;
  }

  @Override
  public void afterReceiveCompletion(
      Message<?> message, MessageChannel messageChannel, Exception e) {}

  @Override
  @CanIgnoreReturnValue
  public Message<?> beforeHandle(
      Message<?> message, MessageChannel channel, MessageHandler handler) {
    if (MessageInvocation.enterDuplicateHandler(message, channel, handler)) {
      // see comment explaining the same conditional in preSend()
      return message;
    }

    MessageWithChannel messageWithChannel = MessageWithChannel.create(message, channel);
    Context ambientContext = Context.current();
    Context parentContext =
        propagators
            .getTextMapPropagator()
            .extract(ambientContext, messageWithChannel, MessageHeadersGetter.INSTANCE);

    Context context = parentContext;
    Context telemetryContext = null;
    Instrumenter<MessageWithChannel, Void> instrumenter = null;
    boolean sameSendInvocation =
        MessageInvocation.currentIsSendFor(ambientContext, message, channel);
    if (!isSpringCloudStreamOutputChannel(channel)
        && !sameSendInvocation
        && consumerInstrumenter.shouldStart(parentContext, messageWithChannel)) {
      telemetryContext = startConsumer(parentContext, messageWithChannel);
      context = telemetryContext;
      instrumenter = consumerInstrumenter;
    }

    MessageHeaderAccessor messageHeaderAccessor = createMutableHeaderAccessor(message);
    propagators
        .getTextMapPropagator()
        .inject(context, messageHeaderAccessor, MessageHeadersSetter.INSTANCE);
    Message<?> outputMessage = createMessageWithHeaders(message, messageHeaderAccessor);
    MessageInvocation.start(
        message,
        outputMessage,
        channel,
        handler,
        messageWithChannel,
        context,
        telemetryContext,
        instrumenter);
    return outputMessage;
  }

  private Context startConsumer(Context parentContext, MessageWithChannel messageWithChannel) {
    try (Scope ignored = Context.root().makeCurrent()) {
      return consumerInstrumenter.start(parentContext, messageWithChannel);
    }
  }

  @Override
  public void afterMessageHandled(
      Message<?> message, MessageChannel channel, MessageHandler handler, Exception ex) {
    MessageInvocation.endHandler(channel, handler, ex);
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
      @SuppressWarnings("unchecked") // cast to actual type
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

  private static boolean isSpringCloudStreamOutputChannel(MessageChannel messageChannel) {
    return "output".equals(getSpringCloudStreamChannelType(messageChannel));
  }

  @Nullable
  private static Object getSpringCloudStreamChannelType(MessageChannel messageChannel) {
    if (DIRECT_WITH_ATTRIBUTES_CHANNEL_CLASS == null || CHANNEL_GET_ATTRIBUTE_MH == null) {
      return null;
    }

    messageChannel = unwrapProxy(messageChannel);
    if (!DIRECT_WITH_ATTRIBUTES_CHANNEL_CLASS.isInstance(messageChannel)) {
      return null;
    }

    try {
      return CHANNEL_GET_ATTRIBUTE_MH.invoke(messageChannel, "type");
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static boolean supportsHandlerInterception(MessageChannel messageChannel) {
    messageChannel = unwrapProxy(messageChannel);
    return messageChannel instanceof ExecutorSubscribableChannel
        || (EXECUTOR_CHANNEL_INTERCEPTOR_AWARE_CLASS != null
            && EXECUTOR_CHANNEL_INTERCEPTOR_AWARE_CLASS.isInstance(messageChannel));
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
    } catch (Throwable ignored) {
      return candidate;
    }
  }
}

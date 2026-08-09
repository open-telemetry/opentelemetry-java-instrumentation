/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7.RabbitCommandInstrumentation.SpanHolder.CURRENT_RABBIT_CONTEXT;
import static io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7.RabbitInstrumenterHelper.helper;
import static io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7.RabbitSingletons.channelInstrumenter;
import static io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7.RabbitSingletons.receiveInstrumenter;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE;
import static net.bytebuddy.matcher.ElementMatchers.canThrow;
import static net.bytebuddy.matcher.ElementMatchers.isGetter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isSetter;
import static net.bytebuddy.matcher.ElementMatchers.nameEndsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.MessageProperties;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import io.opentelemetry.instrumentation.api.internal.Timer;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7.DeliveredMessages.SettledMessages;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class RabbitChannelInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("com.rabbitmq.client.Channel");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("com.rabbitmq.client.Channel"))
        // broken implementation that throws UnsupportedOperationException on getConnection() calls
        .and(not(named("reactor.rabbitmq.ChannelProxy")));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // these transformations need to be applied in a specific order
    transformer.applyAdviceToMethod(
        isMethod()
            .and(
                not(
                    isGetter()
                        .or(isSetter())
                        .or(nameEndsWith("Listener"))
                        .or(nameEndsWith("Listeners"))
                        .or(
                            namedOneOf(
                                "processAsync",
                                "open",
                                "close",
                                "abort",
                                "basicGet",
                                "basicPublish",
                                "basicAck",
                                "basicNack",
                                "basicReject"))))
            .and(isPublic())
            .and(canThrow(IOException.class).or(canThrow(InterruptedException.class))),
        getClass().getName() + "$ChannelMethodAdvice");
    transformer.applyAdviceToMethod(
        named("basicPublish").and(takesArguments(6)),
        getClass().getName() + "$ChannelPublishAdvice");
    // amqp-client 5.30.0 added a ByteBuffer overload that ChannelN implements directly instead of
    // delegating to the byte array one, so it needs its own advice; the trailing WriteListener
    // argument is deliberately neither bound nor referenced, so that this stays loadable on the
    // older versions that the instrumentation still supports
    transformer.applyAdviceToMethod(
        named("basicPublish").and(takesArguments(7)).and(takesArgument(5, ByteBuffer.class)),
        getClass().getName() + "$ChannelPublishByteBufferAdvice");
    transformer.applyAdviceToMethod(
        namedOneOf("basicAck", "basicNack")
            .and(isPublic())
            .and(takesArgument(0, long.class))
            .and(takesArgument(1, boolean.class)),
        getClass().getName() + "$ChannelMultipleSettleAdvice");
    transformer.applyAdviceToMethod(
        named("basicReject").and(isPublic()).and(takesArgument(0, long.class)),
        getClass().getName() + "$ChannelSettleAdvice");
    transformer.applyAdviceToMethod(
        namedOneOf("basicRecover", "basicRecoverAsync").and(isPublic()),
        getClass().getName() + "$ChannelRecoverAdvice");
    transformer.applyAdviceToMethod(
        named("txSelect").and(isPublic()).and(takesArguments(0)),
        getClass().getName() + "$ChannelTxSelectAdvice");
    transformer.applyAdviceToMethod(
        named("txCommit").and(isPublic()).and(takesArguments(0)),
        getClass().getName() + "$ChannelTxCommitAdvice");
    transformer.applyAdviceToMethod(
        named("txRollback").and(isPublic()).and(takesArguments(0)),
        getClass().getName() + "$ChannelTxRollbackAdvice");
    transformer.applyAdviceToMethod(
        named("basicGet").and(takesArgument(0, String.class)).and(takesArgument(1, boolean.class)),
        getClass().getName() + "$ChannelGetAdvice");
    transformer.applyAdviceToMethod(
        named("basicConsume")
            .and(takesArgument(0, String.class))
            .and(takesArgument(1, boolean.class))
            .and(takesArgument(6, named("com.rabbitmq.client.Consumer"))),
        getClass().getName() + "$ChannelConsumeAdvice");
  }

  @SuppressWarnings("unused")
  public static class ChannelMethodAdvice {

    public static class ChannelMethodAdviceScope {
      private final CallDepth callDepth;
      @Nullable private final Context context;
      @Nullable private final Scope scope;
      @Nullable private final ChannelAndMethod request;
      // the deliveries that the instrumented call removed, kept per call rather than per channel so
      // that a failure marks exactly this call's deliveries as forgotten even when another thread
      // settles on the same channel at the same time
      @Nullable private final Channel settledChannel;
      private final long lowestRemovedTag;
      private final long highestRemovedTag;

      private ChannelMethodAdviceScope(
          CallDepth callDepth,
          @Nullable Context context,
          @Nullable Scope scope,
          @Nullable ChannelAndMethod request,
          @Nullable Channel settledChannel,
          long lowestRemovedTag,
          long highestRemovedTag) {
        this.callDepth = callDepth;
        this.context = context;
        this.scope = scope;
        this.request = request;
        this.settledChannel = settledChannel;
        this.lowestRemovedTag = lowestRemovedTag;
        this.highestRemovedTag = highestRemovedTag;
      }

      public static ChannelMethodAdviceScope start(
          CallDepth callDepth, Channel channel, String method, @Nullable Long deliveryTag) {
        return start(callDepth, channel, method, deliveryTag, false);
      }

      public static ChannelMethodAdviceScope start(
          CallDepth callDepth,
          Channel channel,
          String method,
          @Nullable Long deliveryTag,
          boolean multiple) {
        if (callDepth.getAndIncrement() > 0) {
          return new ChannelMethodAdviceScope(callDepth, null, null, null, null, 0, 0);
        }

        Context parentContext = Context.current();
        ChannelAndMethod request;
        Channel settledChannel = null;
        long lowestRemovedTag = 0;
        long highestRemovedTag = 0;
        if (deliveryTag == null) {
          request = ChannelAndMethod.create(channel, method);
        } else {
          // the settled deliveries are removed here, before the call to the broker, so that two
          // threads settling on the same channel can never report the same delivery twice; a
          // settle that then fails records that its deliveries are no longer remembered
          SettledMessages settledMessages =
              DeliveredMessages.settle(channel, deliveryTag, multiple);
          request =
              ChannelAndMethod.createSettle(
                  channel, method, deliveryTag, multiple, settledMessages);
          settledChannel = channel;
          lowestRemovedTag = settledMessages.getLowestRemovedTag();
          highestRemovedTag = settledMessages.getHighestRemovedTag();
        }

        if (!channelInstrumenter(request).shouldStart(parentContext, request)) {
          return new ChannelMethodAdviceScope(
              callDepth, null, null, null, settledChannel, lowestRemovedTag, highestRemovedTag);
        }

        Context context = channelInstrumenter(request).start(parentContext, request);
        CURRENT_RABBIT_CONTEXT.set(context);
        helper().setChannelAndMethod(context, request);

        return new ChannelMethodAdviceScope(
            callDepth,
            context,
            context.makeCurrent(),
            request,
            settledChannel,
            lowestRemovedTag,
            highestRemovedTag);
      }

      public void end(@Nullable Throwable throwable) {
        if (callDepth.decrementAndGet() > 0) {
          return;
        }
        if (throwable != null && settledChannel != null) {
          DeliveredMessages.markForgotten(settledChannel, lowestRemovedTag, highestRemovedTag);
        }
        if (scope == null) {
          return;
        }

        scope.close();

        CURRENT_RABBIT_CONTEXT.remove();
        channelInstrumenter(request).end(context, request, null, throwable);
      }
    }

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static ChannelMethodAdviceScope onEnter(
        @Advice.This Channel channel, @Advice.Origin("Channel.#m") String method) {
      return ChannelMethodAdviceScope.start(
          CallDepth.forClass(Channel.class), channel, method, null);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter ChannelMethodAdviceScope adviceScope) {
      adviceScope.end(throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class ChannelSettleAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static ChannelMethodAdvice.ChannelMethodAdviceScope onEnter(
        @Advice.This Channel channel,
        @Advice.Origin("Channel.#m") String method,
        @Advice.Argument(0) long deliveryTag) {
      return ChannelMethodAdvice.ChannelMethodAdviceScope.start(
          CallDepth.forClass(Channel.class), channel, method, deliveryTag);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter ChannelMethodAdvice.ChannelMethodAdviceScope adviceScope) {
      adviceScope.end(throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class ChannelMultipleSettleAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static ChannelMethodAdvice.ChannelMethodAdviceScope onEnter(
        @Advice.This Channel channel,
        @Advice.Origin("Channel.#m") String method,
        @Advice.Argument(0) long deliveryTag,
        @Advice.Argument(1) boolean multiple) {
      return ChannelMethodAdvice.ChannelMethodAdviceScope.start(
          CallDepth.forClass(Channel.class), channel, method, deliveryTag, multiple);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter ChannelMethodAdvice.ChannelMethodAdviceScope adviceScope) {
      adviceScope.end(throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class ChannelPublishAdvice {

    public static class ChannelPublishAdviceScope {
      private final CallDepth callDepth;
      @Nullable private final Context context;
      @Nullable private final Scope scope;
      @Nullable private final ChannelAndMethod request;

      private ChannelPublishAdviceScope(
          CallDepth callDepth,
          @Nullable Context context,
          @Nullable Scope scope,
          @Nullable ChannelAndMethod request) {
        this.callDepth = callDepth;
        this.context = context;
        this.scope = scope;
        this.request = request;
      }

      @Nullable
      public Context getContext() {
        return context;
      }

      public static ChannelPublishAdviceScope start(
          Channel channel, String exchange, String routingKey) {
        CallDepth callDepth = CallDepth.forClass(Channel.class);
        if (callDepth.getAndIncrement() > 0) {
          return new ChannelPublishAdviceScope(callDepth, null, null, null);
        }

        Context parentContext = Java8BytecodeBridge.currentContext();
        ChannelAndMethod request = ChannelAndMethod.createPublish(channel, exchange, routingKey);

        if (!channelInstrumenter(request).shouldStart(parentContext, request)) {
          return new ChannelPublishAdviceScope(callDepth, null, null, null);
        }

        Context context = channelInstrumenter(request).start(parentContext, request);
        CURRENT_RABBIT_CONTEXT.set(context);
        helper().setChannelAndMethod(context, request);

        return new ChannelPublishAdviceScope(callDepth, context, context.makeCurrent(), request);
      }

      public void end(@Nullable Throwable throwable) {
        if (callDepth.decrementAndGet() > 0) {
          return;
        }
        if (scope == null) {
          return;
        }

        scope.close();

        CURRENT_RABBIT_CONTEXT.remove();
        channelInstrumenter(request).end(context, request, null, throwable);
      }
    }

    @Advice.AssignReturned.ToArguments(
        @Advice.AssignReturned.ToArguments.ToArgument(value = 4, index = 1))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object[] setSpanNameAddHeaders(
        @Advice.This Channel channel,
        @Advice.Argument(0) String exchange,
        @Advice.Argument(1) String routingKey,
        @Advice.Argument(4) AMQP.BasicProperties originalProps,
        @Advice.Argument(5) byte[] body) {
      ChannelPublishAdviceScope adviceScope =
          ChannelPublishAdviceScope.start(channel, exchange, routingKey);

      try {
        return new Object[] {
          adviceScope,
          addHeaders(adviceScope, originalProps, body == null ? null : (long) body.length)
        };
      } catch (Throwable ignored) {
        // the advice suppresses throwables, so a failure here would leave the scope, the call depth
        // and the current rabbit context behind; publish the message without headers instead
        return new Object[] {adviceScope, originalProps};
      }
    }

    public static AMQP.BasicProperties addHeaders(
        ChannelPublishAdviceScope adviceScope,
        @Nullable AMQP.BasicProperties originalProps,
        @Nullable Long bodySize) {
      // when the span was not started, e.g. because it was suppressed, fall back to the current
      // context so that the message headers still get injected
      Context context = adviceScope.getContext();
      if (context == null) {
        context = Java8BytecodeBridge.currentContext();
      }
      Span span = Java8BytecodeBridge.spanFromContext(context);
      AMQP.BasicProperties props = originalProps;

      if (span.getSpanContext().isValid()) {
        if (bodySize != null && emitOldMessagingSemconv()) {
          span.setAttribute(MESSAGING_MESSAGE_BODY_SIZE, bodySize);
        }

        // This is the internal behavior when props are null.  We're just doing it earlier now.
        if (props == null) {
          props = MessageProperties.MINIMAL_BASIC;
        }
        helper().onProps(context, span, props);

        // We need to copy the BasicProperties and provide a header map we can modify
        Map<String, Object> headers = props.getHeaders();
        headers = (headers == null) ? new HashMap<>() : new HashMap<>(headers);

        helper().inject(context, headers, MapSetter.INSTANCE);

        props =
            new AMQP.BasicProperties(
                props.getContentType(),
                props.getContentEncoding(),
                headers,
                props.getDeliveryMode(),
                props.getPriority(),
                props.getCorrelationId(),
                props.getReplyTo(),
                props.getExpiration(),
                props.getMessageId(),
                props.getTimestamp(),
                props.getType(),
                props.getUserId(),
                props.getAppId(),
                props.getClusterId());
      }

      return props;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter @Nullable Object[] enterArgs) {
      if (enterArgs == null) {
        return;
      }
      ((ChannelPublishAdviceScope) enterArgs[0]).end(throwable);
    }
  }

  /**
   * Instruments the {@link ByteBuffer} overload of {@code basicPublish} that amqp-client 5.30.0
   * added. {@code Channel} declares it as a default method that delegates to the byte array
   * overload, but {@code ChannelN} overrides it and publishes directly, so it would otherwise get
   * no span and no context propagation.
   */
  @SuppressWarnings("unused")
  public static class ChannelPublishByteBufferAdvice {

    @Advice.AssignReturned.ToArguments(
        @Advice.AssignReturned.ToArguments.ToArgument(value = 4, index = 1))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object[] setSpanNameAddHeaders(
        @Advice.This Channel channel,
        @Advice.Argument(0) String exchange,
        @Advice.Argument(1) String routingKey,
        @Advice.Argument(4) AMQP.BasicProperties originalProps,
        @Advice.Argument(5) ByteBuffer body) {
      ChannelPublishAdvice.ChannelPublishAdviceScope adviceScope =
          ChannelPublishAdvice.ChannelPublishAdviceScope.start(channel, exchange, routingKey);

      try {
        // remaining() reports the body size without consuming the buffer
        Long bodySize = body == null ? null : (long) body.remaining();
        return new Object[] {
          adviceScope, ChannelPublishAdvice.addHeaders(adviceScope, originalProps, bodySize)
        };
      } catch (Throwable ignored) {
        // the advice suppresses throwables, so a failure here would leave the scope, the call depth
        // and the current rabbit context behind; publish the message without headers instead
        return new Object[] {adviceScope, originalProps};
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter @Nullable Object[] enterArgs) {
      if (enterArgs == null) {
        return;
      }
      ((ChannelPublishAdvice.ChannelPublishAdviceScope) enterArgs[0]).end(throwable);
    }
  }

  /**
   * {@code basicRecover} and {@code basicRecoverAsync} requeue every unacknowledged delivery on the
   * channel, so the broker redelivers them under new delivery tags and the old ones must not be
   * settled again.
   */
  @SuppressWarnings("unused")
  public static class ChannelRecoverAdvice {

    public static class ChannelRecoverAdviceScope {

      public static void end(Channel channel) {
        if (emitStableMessagingSemconv()) {
          DeliveredMessages.clear(channel);
        }
      }

      private ChannelRecoverAdviceScope() {}
    }

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.This Channel channel) {
      ChannelRecoverAdviceScope.end(channel);
    }
  }

  /** {@code txSelect} puts a channel into transaction mode. */
  @SuppressWarnings("unused")
  public static class ChannelTxSelectAdvice {

    public static class ChannelTxSelectAdviceScope {

      public static void end(Channel channel) {
        if (emitStableMessagingSemconv()) {
          DeliveredMessages.selectTransaction(channel);
        }
      }

      private ChannelTxSelectAdviceScope() {}
    }

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.This Channel channel) {
      ChannelTxSelectAdviceScope.end(channel);
    }
  }

  /**
   * {@code txCommit} starts the next transaction, so the settlements made before it can no longer
   * be rolled back.
   */
  @SuppressWarnings("unused")
  public static class ChannelTxCommitAdvice {

    public static class ChannelTxCommitAdviceScope {

      public static void end(Channel channel) {
        if (emitStableMessagingSemconv()) {
          DeliveredMessages.commitTransaction(channel);
        }
      }

      private ChannelTxCommitAdviceScope() {}
    }

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.This Channel channel) {
      ChannelTxCommitAdviceScope.end(channel);
    }
  }

  /**
   * {@code txRollback} undoes the settlements made in the transaction, so the deliveries they
   * settled are outstanding at the broker again while they are no longer remembered.
   */
  @SuppressWarnings("unused")
  public static class ChannelTxRollbackAdvice {

    public static class ChannelTxRollbackAdviceScope {

      public static void end(Channel channel) {
        if (emitStableMessagingSemconv()) {
          DeliveredMessages.markPendingForgotten(channel);
        }
      }

      private ChannelTxRollbackAdviceScope() {}
    }

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.This Channel channel) {
      ChannelTxRollbackAdviceScope.end(channel);
    }
  }

  @SuppressWarnings("unused")
  public static class ChannelGetAdvice {

    public static class ChannelGetAdviceScope {
      private final CallDepth callDepth;
      private final Timer timer;

      private ChannelGetAdviceScope(CallDepth callDepth, Timer timer) {
        this.callDepth = callDepth;
        this.timer = timer;
      }

      public static ChannelGetAdviceScope start() {
        CallDepth callDepth = CallDepth.forClass(Channel.class);
        callDepth.getAndIncrement();
        Timer timer = Timer.start();
        return new ChannelGetAdviceScope(callDepth, timer);
      }

      public void end(
          Channel channel,
          String queue,
          boolean autoAck,
          @Nullable GetResponse response,
          @Nullable Throwable throwable) {
        if (callDepth.decrementAndGet() > 0) {
          return;
        }

        // automatically acknowledged deliveries are never settled by the application, so
        // remembering them would only pollute the deliveries settled by a later multiple settle
        if (response != null && !autoAck && emitStableMessagingSemconv()) {
          DeliveredMessages.record(channel, response.getEnvelope(), queue);
        }

        Context parentContext = Context.current();
        ReceiveRequest request = ReceiveRequest.create(queue, response, channel.getConnection());
        if (!receiveInstrumenter().shouldStart(parentContext, request)) {
          return;
        }

        // can't create span and put into scope in method enter above, because can't add parent
        // after span creation
        InstrumenterUtil.startAndEnd(
            receiveInstrumenter(),
            parentContext,
            request,
            null,
            throwable,
            timer.startTime(),
            timer.now());
      }
    }

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static ChannelGetAdviceScope takeTimestamp() {
      return ChannelGetAdviceScope.start();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void extractAndStartSpan(
        @Advice.This Channel channel,
        @Advice.Argument(0) String queue,
        @Advice.Argument(1) boolean autoAck,
        @Advice.Return @Nullable GetResponse response,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter ChannelGetAdviceScope adviceScope) {
      adviceScope.end(channel, queue, autoAck, response, throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class ChannelConsumeAdvice {

    @Advice.AssignReturned.ToArguments(@Advice.AssignReturned.ToArguments.ToArgument(6))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object wrapConsumer(
        @Advice.This Channel channel,
        @Advice.Argument(0) String queue,
        @Advice.Argument(1) boolean autoAck,
        @Advice.Argument(6) Consumer consumer) {
      // We have to save off the queue name here because it isn't available to the consumer later.
      if (consumer != null && !(consumer instanceof TracedDelegatingConsumer)) {
        return new TracedDelegatingConsumer(
            queue, consumer, autoAck, channel, channel.getConnection());
      }

      return consumer;
    }
  }
}

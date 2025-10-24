/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.rabbitmq.RabbitCommandInstrumentation.SpanHolder.CURRENT_RABBIT_CONTEXT;
import static io.opentelemetry.javaagent.instrumentation.rabbitmq.RabbitInstrumenterHelper.helper;
import static io.opentelemetry.javaagent.instrumentation.rabbitmq.RabbitSingletons.channelInstrumenter;
import static io.opentelemetry.javaagent.instrumentation.rabbitmq.RabbitSingletons.receiveInstrumenter;
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
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class RabbitChannelInstrumentation implements TypeInstrumentation {

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
                        .or(namedOneOf("processAsync", "open", "close", "abort", "basicGet"))))
            .and(isPublic())
            .and(canThrow(IOException.class).or(canThrow(InterruptedException.class))),
        RabbitChannelInstrumentation.class.getName() + "$ChannelMethodAdvice");
    transformer.applyAdviceToMethod(
        isMethod().and(named("basicPublish")).and(takesArguments(6)),
        RabbitChannelInstrumentation.class.getName() + "$ChannelPublishAdvice");
    transformer.applyAdviceToMethod(
        isMethod().and(named("basicGet")).and(takesArgument(0, String.class)),
        RabbitChannelInstrumentation.class.getName() + "$ChannelGetAdvice");
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("basicConsume"))
            .and(takesArgument(0, String.class))
            .and(takesArgument(6, named("com.rabbitmq.client.Consumer"))),
        RabbitChannelInstrumentation.class.getName() + "$ChannelConsumeAdvice");
  }

  // TODO Why do we start span here and not in ChannelPublishAdvice below?
  @SuppressWarnings("unused")
  public static class ChannelMethodAdvice {

    public static class ChannelMethodAdviceScope {
      private final CallDepth callDepth;
      @Nullable private final Context context;
      @Nullable private final Scope scope;
      @Nullable private final ChannelAndMethod request;

      private ChannelMethodAdviceScope(
          CallDepth callDepth,
          @Nullable Context context,
          @Nullable Scope scope,
          @Nullable ChannelAndMethod request) {
        this.callDepth = callDepth;
        this.context = context;
        this.scope = scope;
        this.request = request;
      }

      public static ChannelMethodAdviceScope start(
          CallDepth callDepth, Channel channel, String method) {
        if (callDepth.getAndIncrement() > 0) {
          return new ChannelMethodAdviceScope(callDepth, null, null, null);
        }

        Context parentContext = Context.current();
        ChannelAndMethod request = ChannelAndMethod.create(channel, method);

        if (!channelInstrumenter(request).shouldStart(parentContext, request)) {
          return new ChannelMethodAdviceScope(callDepth, null, null, null);
        }

        Context context = channelInstrumenter(request).start(parentContext, request);
        CURRENT_RABBIT_CONTEXT.set(context);
        helper().setChannelAndMethod(context, request);

        return new ChannelMethodAdviceScope(callDepth, context, context.makeCurrent(), request);
      }

      public void end(Throwable throwable) {
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

    @Advice.OnMethodEnter
    public static ChannelMethodAdviceScope onEnter(
        @Advice.This Channel channel, @Advice.Origin("Channel.#m") String method) {
      return ChannelMethodAdviceScope.start(CallDepth.forClass(Channel.class), channel, method);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter ChannelMethodAdviceScope adviceScope) {
      adviceScope.end(throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class ChannelPublishAdvice {

    @Advice.AssignReturned.ToArguments(@Advice.AssignReturned.ToArguments.ToArgument(4))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AMQP.BasicProperties setSpanNameAddHeaders(
        @Advice.Argument(0) String exchange,
        @Advice.Argument(1) String routingKey,
        @Advice.Argument(4) AMQP.BasicProperties props,
        @Advice.Argument(5) byte[] body) {
      Context context = Java8BytecodeBridge.currentContext();
      Span span = Java8BytecodeBridge.spanFromContext(context);
      AMQP.BasicProperties modifiedProps = props;

      if (span.getSpanContext().isValid()) {
        helper().onPublish(span, exchange, routingKey);
        if (body != null) {
          span.setAttribute(
              MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE, (long) body.length);
        }

        // This is the internal behavior when props are null.  We're just doing it earlier now.
        if (modifiedProps == null) {
          modifiedProps = MessageProperties.MINIMAL_BASIC;
        }
        helper().onProps(context, span, modifiedProps);

        // We need to copy the BasicProperties and provide a header map we can modify
        Map<String, Object> headers = modifiedProps.getHeaders();
        headers = (headers == null) ? new HashMap<>() : new HashMap<>(headers);

        helper().inject(context, headers, MapSetter.INSTANCE);

        modifiedProps =
            new AMQP.BasicProperties(
                modifiedProps.getContentType(),
                modifiedProps.getContentEncoding(),
                headers,
                modifiedProps.getDeliveryMode(),
                modifiedProps.getPriority(),
                modifiedProps.getCorrelationId(),
                modifiedProps.getReplyTo(),
                modifiedProps.getExpiration(),
                modifiedProps.getMessageId(),
                modifiedProps.getTimestamp(),
                modifiedProps.getType(),
                modifiedProps.getUserId(),
                modifiedProps.getAppId(),
                modifiedProps.getClusterId());
      }

      return modifiedProps;
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

      public void end(Channel channel, String queue, GetResponse response, Throwable throwable) {
        if (callDepth.decrementAndGet() > 0) {
          return;
        }

        Context parentContext = Context.current();
        ReceiveRequest request = ReceiveRequest.create(queue, response, channel.getConnection());
        if (!receiveInstrumenter().shouldStart(parentContext, request)) {
          return;
        }

        // can't create span and put into scope in method enter above, because can't add parent
        // after
        // span creation
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

    @Advice.OnMethodEnter
    public static ChannelGetAdviceScope takeTimestamp() {
      return ChannelGetAdviceScope.start();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void extractAndStartSpan(
        @Advice.This Channel channel,
        @Advice.Argument(0) String queue,
        @Advice.Return GetResponse response,
        @Advice.Thrown Throwable throwable,
        @Advice.Enter ChannelGetAdviceScope adviceScope) {
      adviceScope.end(channel, queue, response, throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class ChannelConsumeAdvice {

    @Advice.AssignReturned.ToArguments(@Advice.AssignReturned.ToArguments.ToArgument(6))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Object wrapConsumer(
        @Advice.This Channel channel,
        @Advice.Argument(0) String queue,
        @Advice.Argument(6) Consumer consumer) {
      // We have to save off the queue name here because it isn't available to the consumer later.
      Consumer modifiedConsumer = consumer;
      if (modifiedConsumer != null && !(modifiedConsumer instanceof TracedDelegatingConsumer)) {
        modifiedConsumer =
            new TracedDelegatingConsumer(queue, modifiedConsumer, channel.getConnection());
      }

      return modifiedConsumer;
    }
  }
}

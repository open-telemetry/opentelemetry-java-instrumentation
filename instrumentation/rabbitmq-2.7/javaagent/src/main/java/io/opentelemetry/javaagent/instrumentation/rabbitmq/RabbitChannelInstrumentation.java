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
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
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

    @Advice.OnMethodEnter
    public static void onEnter(
        @Advice.This Channel channel,
        @Advice.Origin("Channel.#m") String method,
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Local("otelRequest") ChannelAndMethod request) {
      callDepth = CallDepth.forClass(Channel.class);
      if (callDepth.getAndIncrement() > 0) {
        return;
      }

      Context parentContext = Java8BytecodeBridge.currentContext();
      request = ChannelAndMethod.create(channel, method);

      if (!channelInstrumenter().shouldStart(parentContext, request)) {
        return;
      }

      context = channelInstrumenter().start(parentContext, request);
      CURRENT_RABBIT_CONTEXT.set(context);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Local("otelRequest") ChannelAndMethod request) {
      if (callDepth.decrementAndGet() > 0) {
        return;
      }

      scope.close();

      CURRENT_RABBIT_CONTEXT.remove();
      channelInstrumenter().end(context, request, null, throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class ChannelPublishAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void setSpanNameAddHeaders(
        @Advice.Argument(0) String exchange,
        @Advice.Argument(1) String routingKey,
        @Advice.Argument(value = 4, readOnly = false) AMQP.BasicProperties props,
        @Advice.Argument(5) byte[] body) {
      Context context = Java8BytecodeBridge.currentContext();
      Span span = Java8BytecodeBridge.spanFromContext(context);

      if (span.getSpanContext().isValid()) {
        helper().onPublish(span, exchange, routingKey);
        if (body != null) {
          span.setAttribute(
              SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES, (long) body.length);
        }

        // This is the internal behavior when props are null.  We're just doing it earlier now.
        if (props == null) {
          props = MessageProperties.MINIMAL_BASIC;
        }
        helper().onProps(span, props);

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
    }
  }

  @SuppressWarnings("unused")
  public static class ChannelGetAdvice {

    @Advice.OnMethodEnter
    public static void takeTimestamp(
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Local("otelTimer") Timer timer) {
      callDepth = CallDepth.forClass(Channel.class);
      callDepth.getAndIncrement();
      timer = Timer.start();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void extractAndStartSpan(
        @Advice.This Channel channel,
        @Advice.Argument(0) String queue,
        @Advice.Return GetResponse response,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Local("otelTimer") Timer timer) {
      if (callDepth.decrementAndGet() > 0) {
        return;
      }

      Context parentContext = Java8BytecodeBridge.currentContext();
      ReceiveRequest request = ReceiveRequest.create(queue, response, channel.getConnection());
      if (!receiveInstrumenter().shouldStart(parentContext, request)) {
        return;
      }

      // can't create span and put into scope in method enter above, because can't add parent after
      // span creation
      InstrumenterUtil.startAndEnd(
          receiveInstrumenter(),
          parentContext,
          request,
          null,
          throwable,
          timer.startTimeNanos(),
          timer.nowNanos());
    }
  }

  @SuppressWarnings("unused")
  public static class ChannelConsumeAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void wrapConsumer(
        @Advice.Argument(0) String queue,
        @Advice.Argument(value = 6, readOnly = false) Consumer consumer) {
      // We have to save off the queue name here because it isn't available to the consumer later.
      if (consumer != null && !(consumer instanceof TracedDelegatingConsumer)) {
        consumer = new TracedDelegatingConsumer(queue, consumer);
      }
    }
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import static io.opentelemetry.javaagent.instrumentation.rabbitmq.RabbitCommandInstrumentation.SpanHolder.CURRENT_RABBIT_CONTEXT;
import static io.opentelemetry.javaagent.instrumentation.rabbitmq.RabbitTracer.tracer;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.NameMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.canThrow;
import static net.bytebuddy.matcher.ElementMatchers.isGetter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isSetter;
import static net.bytebuddy.matcher.ElementMatchers.nameEndsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
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
import io.opentelemetry.javaagent.instrumentation.api.CallDepthThreadLocalMap;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class RabbitChannelInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("com.rabbitmq.client.Channel");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("com.rabbitmq.client.Channel"));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    // We want the advice applied in a specific order, so use an ordered map.
    Map<ElementMatcher<? super MethodDescription>, String> transformers = new LinkedHashMap<>();
    transformers.put(
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
    transformers.put(
        isMethod().and(named("basicPublish")).and(takesArguments(6)),
        RabbitChannelInstrumentation.class.getName() + "$ChannelPublishAdvice");
    transformers.put(
        isMethod().and(named("basicGet")).and(takesArgument(0, String.class)),
        RabbitChannelInstrumentation.class.getName() + "$ChannelGetAdvice");
    transformers.put(
        isMethod()
            .and(named("basicConsume"))
            .and(takesArgument(0, String.class))
            .and(takesArgument(6, named("com.rabbitmq.client.Consumer"))),
        RabbitChannelInstrumentation.class.getName() + "$ChannelConsumeAdvice");
    return transformers;
  }

  // TODO Why do we start span here and not in ChannelPublishAdvice below?
  public static class ChannelMethodAdvice {
    @Advice.OnMethodEnter
    public static void onEnter(
        @Advice.This Channel channel,
        @Advice.Origin("Channel.#m") String method,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      int callDepth = CallDepthThreadLocalMap.incrementCallDepth(Channel.class);
      if (callDepth > 0) {
        return;
      }

      context = tracer().startSpan(method, channel.getConnection());
      CURRENT_RABBIT_CONTEXT.set(context);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }
      scope.close();
      CallDepthThreadLocalMap.reset(Channel.class);

      CURRENT_RABBIT_CONTEXT.remove();
      if (throwable != null) {
        tracer().endExceptionally(context, throwable);
      } else {
        tracer().end(context);
      }
    }
  }

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
        tracer().onPublish(span, exchange, routingKey);
        if (body != null) {
          span.setAttribute(
              SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES, (long) body.length);
        }

        // This is the internal behavior when props are null.  We're just doing it earlier now.
        if (props == null) {
          props = MessageProperties.MINIMAL_BASIC;
        }
        Integer deliveryMode = props.getDeliveryMode();
        if (deliveryMode != null) {
          span.setAttribute("rabbitmq.delivery_mode", deliveryMode);
        }

        // We need to copy the BasicProperties and provide a header map we can modify
        Map<String, Object> headers = props.getHeaders();
        headers = (headers == null) ? new HashMap<>() : new HashMap<>(headers);

        tracer().inject(context, headers, TextMapInjectAdapter.SETTER);

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

  public static class ChannelGetAdvice {
    @Advice.OnMethodEnter
    public static long takeTimestamp(@Advice.Local("callDepth") int callDepth) {

      callDepth = CallDepthThreadLocalMap.incrementCallDepth(Channel.class);
      return System.currentTimeMillis();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void extractAndStartSpan(
        @Advice.This Channel channel,
        @Advice.Argument(0) String queue,
        @Advice.Enter long startTime,
        @Advice.Local("callDepth") int callDepth,
        @Advice.Return GetResponse response,
        @Advice.Thrown Throwable throwable) {
      if (callDepth > 0) {
        return;
      }
      CallDepthThreadLocalMap.reset(Channel.class);

      // can't create span and put into scope in method enter above, because can't add parent after
      // span creation
      Context context = tracer().startGetSpan(queue, startTime, response, channel.getConnection());
      if (throwable != null) {
        tracer().endExceptionally(context, throwable);
      } else {
        tracer().end(context);
      }
    }
  }

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

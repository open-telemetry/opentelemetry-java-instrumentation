/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar;

import static io.opentelemetry.javaagent.instrumentation.pulsar.PulsarTelemetry.CONSUMER_NAME;
import static io.opentelemetry.javaagent.instrumentation.pulsar.PulsarTelemetry.MESSAGE_ID;
import static io.opentelemetry.javaagent.instrumentation.pulsar.PulsarTelemetry.SUBSCRIPTION;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.MessagingDestinationKindValues.TOPIC;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.pulsar.info.MessageEnhanceInfo;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageListener;
import org.apache.pulsar.client.impl.conf.ConsumerConfigurationData;

public class MessageListenerInstrumentation implements TypeInstrumentation {
  private static final Tracer TRACER = PulsarTelemetry.tracer();

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // return hasSuperType(named("org.apache.pulsar.client.api.MessageListener"));
    // can't enhance MessageListener here like above due to jvm can't enhance lambda.
    return named("org.apache.pulsar.client.impl.conf.ConsumerConfigurationData");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(isPublic()).and(named("getMessageListener")),
        MessageListenerInstrumentation.class.getName() + "$ConsumerConfigurationDataMethodAdviser");
  }

  @SuppressWarnings("unused")
  public static class ConsumerConfigurationDataMethodAdviser {

    @Advice.OnMethodExit
    @Advice.AssignReturned.ToReturned
    public static MessageListener<?> after(
        @Advice.This ConsumerConfigurationData<?> data,
        @Advice.Return MessageListener<?> listener) {
      if (null == listener) {
        return null;
      }

      return new MessageListenerWrapper<>(listener);
    }
  }

  public static class MessageListenerWrapper<T> implements MessageListener<T> {
    private static final long serialVersionUID = 1L;

    private final MessageListener<T> delegator;

    public MessageListenerWrapper(MessageListener<T> messageListener) {
      this.delegator = messageListener;
    }

    @Override
    public void received(Consumer<T> consumer, Message<T> msg) {
      MessageEnhanceInfo info = MessageEnhanceInfo.virtualField(msg);
      Context parent = info == null ? Context.current() : info.getContext();
      String topic = null == info ? consumer.getTopic() : info.getTopic();
      String mid = null == info ? "unknown" : info.getMessageId();

      Span span =
          TRACER
              .spanBuilder("Pulsar://MessageListener/received")
              .setParent(parent)
              .setSpanKind(SpanKind.CONSUMER)
              .setAttribute(TOPIC, topic)
              .setAttribute(MESSAGE_ID, mid)
              .setAttribute(SUBSCRIPTION, consumer.getSubscription())
              .setAttribute(CONSUMER_NAME, consumer.getConsumerName())
              .startSpan();

      try (Scope scope = span.makeCurrent()) {
        this.delegator.received(consumer, msg);
      } catch (Throwable t) {
        span.recordException(t);
        throw t;
      } finally {
        span.end();
      }
    }

    @Override
    public void reachedEndOfTopic(Consumer<T> consumer) {
      this.delegator.reachedEndOfTopic(consumer);
    }
  }
}

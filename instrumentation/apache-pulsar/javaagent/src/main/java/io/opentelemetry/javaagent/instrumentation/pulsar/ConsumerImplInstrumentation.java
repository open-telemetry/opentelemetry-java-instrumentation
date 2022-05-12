/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar;

import static io.opentelemetry.javaagent.instrumentation.pulsar.PulsarTelemetry.CONSUMER_NAME;
import static io.opentelemetry.javaagent.instrumentation.pulsar.PulsarTelemetry.PROPAGATOR;
import static io.opentelemetry.javaagent.instrumentation.pulsar.PulsarTelemetry.SERVICE_URL;
import static io.opentelemetry.javaagent.instrumentation.pulsar.PulsarTelemetry.SUBSCRIPTION;
import static io.opentelemetry.javaagent.instrumentation.pulsar.PulsarTelemetry.TOPIC;
import static io.opentelemetry.javaagent.instrumentation.pulsar.PulsarTelemetry.TRACER;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.pulsar.info.ClientEnhanceInfo;
import io.opentelemetry.javaagent.instrumentation.pulsar.info.MessageEnhanceInfo;
import io.opentelemetry.javaagent.instrumentation.pulsar.textmap.MessageTextMapGetter;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.impl.ConsumerImpl;
import org.apache.pulsar.client.impl.MessageImpl;
import org.apache.pulsar.client.impl.PulsarClientImpl;

public class ConsumerImplInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.pulsar.client.impl.ConsumerImpl");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    String klassName = ConsumerImplInstrumentation.class.getName();

    transformer.applyAdviceToMethod(isConstructor(), klassName + "$ConsumerImplConstructorAdviser");

    transformer.applyAdviceToMethod(
        isMethod()
            .and(isProtected())
            .and(named("messageProcessed"))
            .and(takesArgument(0, named("org.apache.pulsar.client.api.Message"))),
        klassName + "$ConsumerImplMethodAdviser");
  }

  @SuppressWarnings("unused")
  public static class ConsumerImplConstructorAdviser {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void after(
        @Advice.This ConsumerImpl<?> consumer,
        @Advice.Argument(value = 0) PulsarClient client,
        @Advice.Argument(value = 1) String topic) {

      PulsarClientImpl pulsarClient = (PulsarClientImpl) client;
      String url = pulsarClient.getLookup().getServiceUrl();

      ClientEnhanceInfo info = new ClientEnhanceInfo(topic, url);
      ClientEnhanceInfo.setConsumerEnhancedField(consumer, info);
    }
  }

  @SuppressWarnings("unused")
  public static class ConsumerImplMethodAdviser {

    @Advice.OnMethodEnter
    public static void before(
        @Advice.This ConsumerImpl<?> consumer,
        @Advice.Argument(value = 0) Message<?> message,
        @Advice.Local(value = "otelScope") Scope scope) {
      ClientEnhanceInfo info = ClientEnhanceInfo.getConsumerEnhancedField(consumer);
      if (null == info) {
        scope = Scope.noop();
      }

      MessageImpl<?> messageImpl = (MessageImpl<?>) message;
      Context context =
          PROPAGATOR.extract(Context.current(), messageImpl, MessageTextMapGetter.INSTANCE);

      scope = TRACER
          .spanBuilder("ConsumerImpl/messageProcessed")
          .setParent(context)
          .setSpanKind(SpanKind.CONSUMER)
          .setAttribute(TOPIC, info.topic)
          .setAttribute(SERVICE_URL, info.brokerUrl)
          .setAttribute(SUBSCRIPTION, consumer.getSubscription())
          .setAttribute(CONSUMER_NAME, consumer.getConsumerName())
          .startSpan()
          .makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void after(
        @Advice.This ConsumerImpl<?> consumer,
        @Advice.Argument(value = 0) Message<?> message,
        @Advice.Thrown Throwable t,
        @Advice.Local(value = "otelScope") Scope scope) {
      ClientEnhanceInfo info = ClientEnhanceInfo.getConsumerEnhancedField(consumer);
      if (null == info || null == scope) {
        if (null != scope) {
          scope.close();
        }
      } else {
        MessageEnhanceInfo messageInfo = MessageEnhanceInfo.getMessageEnhancedField(message);
        if (null != messageInfo) {
          messageInfo.setFields(Context.current(), consumer.getTopic(), message.getMessageId());
        }

        Span span = Span.current();
        if (t != null) {
          span.recordException(t);
        }

        span.end();
        scope.close();
      }
    }

  }
}

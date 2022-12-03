/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.pulsar.telemetry.PulsarTelemetry;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.impl.ConsumerImpl;
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
    private ConsumerImplConstructorAdviser() {}

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void after(
        @Advice.This ConsumerImpl<?> consumer,
        @Advice.Argument(value = 0) PulsarClient client,
        @Advice.Argument(value = 1) String topic) {

      PulsarClientImpl pulsarClient = (PulsarClientImpl) client;
      String url = pulsarClient.getLookup().getServiceUrl();
      ClientEnhanceInfo info = ClientEnhanceInfo.create(topic, url);
      VirtualFieldStore.inject(consumer, info);
    }
  }

  @SuppressWarnings("unused")
  public static class ConsumerImplMethodAdviser {
    private ConsumerImplMethodAdviser() {}

    @Advice.OnMethodEnter
    public static void before(
        @Advice.This ConsumerImpl<?> consumer,
        @Advice.Argument(value = 0) Message<?> message,
        @Advice.Local(value = "otelScope") Scope scope) {
      Instrumenter<Message<?>, Attributes> instrumenter =
          PulsarTelemetry.consumerReceiveInstrumenter();

      Context parent = Context.current();
      if (instrumenter.shouldStart(parent, message)) {
        scope = instrumenter.start(parent, message).makeCurrent();
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void after(
        @Advice.This ConsumerImpl<?> consumer,
        @Advice.Argument(value = 0) Message<?> message,
        @Advice.Thrown Throwable t,
        @Advice.Local(value = "otelScope") Scope scope) {
      if (scope == null) {
        return;
      }

      ClientEnhanceInfo cinfo = VirtualFieldStore.extract(consumer);
      String topic = null == cinfo ? ClientEnhanceInfo.DEFAULT_TOPIC : cinfo.topic;
      String brokerUrl = null == cinfo ? ClientEnhanceInfo.DEFAULT_BROKER_URL : cinfo.brokerUrl;
      Attributes attributes =
          Attributes.of(
              SemanticAttributes.MESSAGING_URL, brokerUrl,
              SemanticAttributes.MESSAGING_DESTINATION, topic);

      Context current = Context.current();
      VirtualFieldStore.inject(message, current);

      Instrumenter<Message<?>, Attributes> instrumenter =
          PulsarTelemetry.consumerReceiveInstrumenter();
      instrumenter.end(current, message, attributes, t);
      scope.close();
    }
  }
}

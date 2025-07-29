/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8;

import static io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry.PulsarSingletons.producerInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry.PulsarRequest;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.impl.ProducerImpl;
import org.apache.pulsar.client.impl.PulsarClientImpl;
import org.apache.pulsar.client.impl.SendCallback;

public class ProducerImplInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.pulsar.client.impl.ProducerImpl");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(isPublic())
            .and(
                takesArgument(0, hasSuperType(named("org.apache.pulsar.client.api.PulsarClient")))),
        ProducerImplInstrumentation.class.getName() + "$ProducerImplConstructorAdvice");

    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("sendAsync"))
            .and(takesArgument(1, named("org.apache.pulsar.client.impl.SendCallback"))),
        ProducerImplInstrumentation.class.getName() + "$ProducerSendAsyncMethodAdvice");
  }

  @SuppressWarnings("unused")
  public static class ProducerImplConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void intercept(
        @Advice.This ProducerImpl<?> producer, @Advice.Argument(value = 0) PulsarClient client) {
      PulsarClientImpl pulsarClient = (PulsarClientImpl) client;
      String brokerUrl = pulsarClient.getLookup().getServiceUrl();
      String topic = producer.getTopic();
      VirtualFieldStore.inject(producer, brokerUrl, topic);
    }
  }

  @SuppressWarnings("unused")
  public static class ProducerSendAsyncMethodAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void before(
        @Advice.This ProducerImpl<?> producer,
        @Advice.Argument(value = 0) Message<?> message,
        @Advice.Argument(value = 1) SendCallback callback) {
      Context parent = Context.current();
      PulsarRequest request = PulsarRequest.create(message, VirtualFieldStore.extract(producer));

      if (!producerInstrumenter().shouldStart(parent, request)) {
        return;
      }

      Context context = producerInstrumenter().start(parent, request);
      // Inject the context/request into the SendCallback. This will be extracted and used when the
      // message is sent and the callback is invoked. see `SendCallbackInstrumentation`.
      VirtualFieldStore.inject(callback, context, request);
    }
  }
}

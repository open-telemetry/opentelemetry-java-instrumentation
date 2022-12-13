/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v28;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.pulsar.v28.telemetry.PulsarSingletons;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.concurrent.CompletableFuture;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.impl.MessageImpl;
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
        ProducerImplInstrumentation.class.getName() + "$ProducerImplConstructorAdviser");

    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("sendAsync"))
            .and(takesArgument(1, named("org.apache.pulsar.client.impl.SendCallback"))),
        ProducerImplInstrumentation.class.getName() + "$ProducerSendAsyncMethodAdviser");
  }

  @SuppressWarnings("unused")
  public static class ProducerImplConstructorAdviser {
    private ProducerImplConstructorAdviser() {}

    @Advice.OnMethodExit
    public static void intercept(
        @Advice.This ProducerImpl<?> producer, @Advice.Argument(value = 0) PulsarClient client) {
      PulsarClientImpl pulsarClient = (PulsarClientImpl) client;
      String brokerUrl = pulsarClient.getLookup().getServiceUrl();
      String topic = producer.getTopic();
      topic = topic == null ? "unknown" : topic;
      brokerUrl = brokerUrl == null ? "unknown" : topic;
      VirtualFieldStore.inject(producer, brokerUrl, topic);
    }
  }

  @SuppressWarnings("unused")
  public static class ProducerSendAsyncMethodAdviser {
    private ProducerSendAsyncMethodAdviser() {}

    @Advice.OnMethodEnter
    public static void before(
        @Advice.This ProducerImpl<?> producer,
        @Advice.Argument(value = 0) Message<?> message,
        @Advice.Argument(value = 1, readOnly = false) SendCallback callback) {
      Context parent = Context.current();
      Instrumenter<Message<?>, Attributes> instrumenter = PulsarSingletons.producerInstrumenter();

      Context current = null;
      if (instrumenter.shouldStart(parent, message)) {
        current = instrumenter.start(parent, message);
      }

      callback = new SendCallbackWrapper(current, message, callback, producer);
    }
  }

  public static class SendCallbackWrapper implements SendCallback {

    private final Context context;
    private final Message<?> message;
    private final SendCallback delegator;
    private final ProducerImpl<?> producer;

    public SendCallbackWrapper(
        Context context, Message<?> message, SendCallback callback, ProducerImpl<?> producer) {
      this.context = context;
      this.message = message;
      this.delegator = callback;
      this.producer = producer;
    }

    @Override
    public void sendComplete(Exception e) {
      if (null == context) {
        this.delegator.sendComplete(e);
        return;
      }

      Instrumenter<Message<?>, Attributes> instrumenter = PulsarSingletons.producerInstrumenter();
      StringTuple2 tuple2 = VirtualFieldStore.extract(producer);
      Attributes attributes =
          Attributes.of(
              SemanticAttributes.MESSAGING_URL,
              tuple2.f1,
              SemanticAttributes.MESSAGING_DESTINATION,
              tuple2.f2);

      try (Scope ignore = context.makeCurrent()) {
        this.delegator.sendComplete(e);
      } finally {
        instrumenter.end(context, message, attributes, e);
      }
    }

    @Override
    public void addCallback(MessageImpl<?> msg, SendCallback scb) {
      this.delegator.addCallback(msg, scb);
    }

    @Override
    public SendCallback getNextSendCallback() {
      return this.delegator.getNextSendCallback();
    }

    @Override
    public MessageImpl<?> getNextMessage() {
      return this.delegator.getNextMessage();
    }

    @Override
    public CompletableFuture<MessageId> getFuture() {
      return this.delegator.getFuture();
    }
  }
}

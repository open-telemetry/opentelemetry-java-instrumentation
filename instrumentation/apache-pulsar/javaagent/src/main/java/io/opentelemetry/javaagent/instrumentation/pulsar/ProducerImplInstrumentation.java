/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar;

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
import io.opentelemetry.javaagent.instrumentation.pulsar.telemetry.PulsarTelemetry;
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
        isConstructor().and(isPublic()),
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
        @Advice.This ProducerImpl<?> producer,
        @Advice.Argument(value = 0) PulsarClient client,
        @Advice.Argument(value = 1) String topic) {
      PulsarClientImpl pulsarClient = (PulsarClientImpl) client;
      String url = pulsarClient.getLookup().getServiceUrl();
      ClientEnhanceInfo info = ClientEnhanceInfo.create(topic, url);
      VirtualFieldStore.inject(producer, info);
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
      ClientEnhanceInfo info = VirtualFieldStore.extract(producer);
      Instrumenter<Message<?>, Attributes> instrumenter = PulsarTelemetry.producerInstrumenter();

      Context current = null;
      if (instrumenter.shouldStart(parent, message)) {
        current = instrumenter.start(Context.current(), message);
      }

      callback = new SendCallbackWrapper(current, message, callback, info);
    }
  }

  public static class SendCallbackWrapper implements SendCallback {
    private static final long serialVersionUID = 1L;

    private final Context context;
    private final Message<?> message;
    private final SendCallback delegator;
    private final ClientEnhanceInfo info;

    public SendCallbackWrapper(
        Context context, Message<?> message, SendCallback callback, ClientEnhanceInfo info) {
      this.context = context;
      this.message = message;
      this.delegator = callback;
      this.info = info;
    }

    @Override
    public void sendComplete(Exception e) {
      if (null == context) {
        this.delegator.sendComplete(e);
        return;
      }

      Instrumenter<Message<?>, Attributes> instrumenter = PulsarTelemetry.producerInstrumenter();
      Attributes attributes = Attributes.empty();
      if (null != info) {
        attributes = Attributes.of(SemanticAttributes.MESSAGING_URL, info.brokerUrl);
      }

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

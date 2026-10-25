/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8;

import static io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry.PulsarSingletons.producerInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry.PulsarRequest;
import io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry.PulsarSingletons;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.impl.ProducerImpl;
import org.apache.pulsar.client.impl.PulsarClientImpl;
import org.apache.pulsar.client.impl.SendCallback;

class ProducerImplInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.pulsar.client.impl.ProducerImpl");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("internalSendAsync")
            .and(takesArgument(0, named("org.apache.pulsar.client.api.Message"))),
        getClass().getName() + "$InternalSendAsyncAdvice");

    transformer.applyAdviceToMethod(
        isConstructor()
            .and(isPublic())
            .and(takesArgument(0, named("org.apache.pulsar.client.impl.PulsarClientImpl"))),
        getClass().getName() + "$ProducerImplConstructorAdvice");

    transformer.applyAdviceToMethod(
        named("sendAsync")
            .and(takesArgument(0, named("org.apache.pulsar.client.api.Message")))
            .and(takesArgument(1, named("org.apache.pulsar.client.impl.SendCallback"))),
        getClass().getName() + "$ProducerSendAsyncMethodAdvice");
  }

  @SuppressWarnings("unused")
  public static class ProducerImplConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void intercept(
        @Advice.This ProducerImpl<?> producer,
        @Advice.Argument(value = 0) PulsarClientImpl client) {
      String brokerUrl = client.getLookup().getServiceUrl();
      String topic = producer.getTopic();
      VirtualFieldStore.inject(producer, brokerUrl, topic);
    }
  }

  @SuppressWarnings("unused")
  public static class InternalSendAsyncAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope onEnter(
        @Advice.This ProducerImpl<?> producer, @Advice.Argument(0) Message<?> message) {
      Context parent = Context.current();
      PulsarRequest request = PulsarRequest.create(message, VirtualFieldStore.extract(producer));
      if (!producerInstrumenter().shouldStart(parent, request)) {
        // A reentrant, suppressed send must not attach its callback to the outer operation.
        return new AdviceScope(null, SendCallbackData.withoutOperation(parent).makeCurrent());
      }
      Context context = producerInstrumenter().start(parent, request);
      SendCallbackData data = SendCallbackData.create(parent, context, request);
      return new AdviceScope(data, data.preparationContext().makeCurrent());
    }

    @AssignReturned.ToReturned
    @Nullable
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static CompletableFuture<MessageId> onExit(
        @Advice.Enter @Nullable AdviceScope state,
        @Advice.Return @Nullable CompletableFuture<MessageId> future,
        @Advice.Thrown @Nullable Throwable error) {
      if (state == null) {
        return future;
      }
      state.scope.close();
      if (state.data == null) {
        return future;
      }
      if (error != null || future == null) {
        state.data.end(error);
        return future;
      }
      return state.data.wrap(future);
    }

    public static class AdviceScope {
      @Nullable final SendCallbackData data;
      final Scope scope;

      AdviceScope(@Nullable SendCallbackData data, Scope scope) {
        this.data = data;
        this.scope = scope;
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ProducerSendAsyncMethodAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void before(
        @Advice.This ProducerImpl<?> producer,
        @Advice.Argument(0) Message<?> message,
        @Advice.Argument(1) SendCallback callback) {
      SendCallbackData data = SendCallbackData.current();
      if (data != null) {
        // An interceptor can replace the message after the span was started.
        if (message != data.request.getMessage()) {
          data.request = PulsarRequest.create(message, VirtualFieldStore.extract(producer));
        }
        PulsarSingletons.inject(data.context, data.request);
        VirtualFieldStore.inject(callback, data);
      }
    }
  }
}

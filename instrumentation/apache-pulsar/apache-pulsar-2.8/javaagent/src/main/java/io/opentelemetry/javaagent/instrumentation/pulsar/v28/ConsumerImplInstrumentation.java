/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v28;

import static io.opentelemetry.javaagent.instrumentation.pulsar.v28.telemetry.PulsarSingletons.startAndEndConsumerReceive;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.concurrent.CompletableFuture;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.impl.PulsarClientImpl;

public class ConsumerImplInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.pulsar.client.impl.ConsumerImpl")
        .or(named("org.apache.pulsar.client.impl.MultiTopicsConsumerImpl"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    String klassName = ConsumerImplInstrumentation.class.getName();

    transformer.applyAdviceToMethod(isConstructor(), klassName + "$ConsumerConstructorAdviser");

    // internalReceive will apply to Consumer#receive(long,TimeUnit)
    // and called before MessageListener#receive.
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isProtected())
            .and(named("internalReceive"))
            .and(takesArguments(2))
            .and(takesArgument(1, named("java.util.concurrent.TimeUnit"))),
        klassName + "$ConsumerInternalReceiveAdviser");
    // receive/batchReceive will apply to Consumer#receive()/Consumer#batchReceive()
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("receive").or(named("batchReceive")))
            .and(takesArguments(0)),
        klassName + "$ConsumerSyncReceiveAdviser");
    // receiveAsync/batchReceiveAsync will apply to
    // Consumer#receiveAsync()/Consumer#batchReceiveAsync()
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("receiveAsync").or(named("batchReceiveAsync")))
            .and(takesArguments(0)),
        klassName + "$ConsumerAsyncReceiveAdviser");
  }

  @SuppressWarnings("unused")
  public static class ConsumerConstructorAdviser {
    private ConsumerConstructorAdviser() {}

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void after(
        @Advice.This Consumer<?> consumer, @Advice.Argument(value = 0) PulsarClient client) {

      PulsarClientImpl pulsarClient = (PulsarClientImpl) client;
      String url = pulsarClient.getLookup().getServiceUrl();
      VirtualFieldStore.inject(consumer, url);
    }
  }

  @SuppressWarnings("unused")
  public static class ConsumerInternalReceiveAdviser {
    private ConsumerInternalReceiveAdviser() {}

    @Advice.OnMethodEnter
    public static void before(@Advice.Local(value = "startTime") long startTime) {
      startTime = System.currentTimeMillis();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void after(
        @Advice.This Consumer<?> consumer,
        @Advice.Return Message<?> message,
        @Advice.Thrown Throwable t,
        @Advice.Local(value = "startTime") long startTime) {
      if (t != null) {
        return;
      }

      Context parent = Context.current();
      Context current = startAndEndConsumerReceive(parent, message, startTime, consumer);
      if (current != null) {
        // ConsumerBase#internalReceive(long,TimeUnit) will be called before
        // ConsumerListener#receive(Consumer,Message), so, need to inject Context into Message.
        VirtualFieldStore.inject(message, current);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ConsumerSyncReceiveAdviser {
    private ConsumerSyncReceiveAdviser() {}

    @Advice.OnMethodEnter
    public static void before(@Advice.Local(value = "startTime") long startTime) {
      startTime = System.currentTimeMillis();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void after(
        @Advice.This Consumer<?> consumer,
        @Advice.Return Message<?> message,
        @Advice.Thrown Throwable t,
        @Advice.Local(value = "startTime") long startTime) {
      if (t != null) {
        return;
      }

      Context parent = Context.current();
      startAndEndConsumerReceive(parent, message, startTime, consumer);
      // No need to inject context to message.
    }
  }

  @SuppressWarnings("unused")
  public static class ConsumerAsyncReceiveAdviser {
    private ConsumerAsyncReceiveAdviser() {}

    @Advice.OnMethodEnter
    public static void before(@Advice.Local(value = "startTime") long startTime) {
      startTime = System.currentTimeMillis();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void after(
        @Advice.This Consumer<?> consumer,
        @Advice.Return CompletableFuture<Message<?>> future,
        @Advice.Local(value = "startTime") long startTime) {
      future.whenComplete(
          (message, t) -> {
            if (t != null) {
              return;
            }

            Context parent = Context.current();
            startAndEndConsumerReceive(parent, message, startTime, consumer);
            // No need to inject context to message.
          });
    }
  }
}

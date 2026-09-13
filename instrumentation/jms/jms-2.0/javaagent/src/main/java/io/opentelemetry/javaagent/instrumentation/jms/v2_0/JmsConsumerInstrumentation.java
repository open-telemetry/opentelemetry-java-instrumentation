/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v2_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.jms.common.v1_1.JmsReceiveSpanUtil.createReceiveSpan;
import static io.opentelemetry.javaagent.instrumentation.jms.v2_0.JmsSingletons.consumerReceiveInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.instrumentation.api.internal.Timer;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.jms.common.v1_1.MessageWithDestination;
import io.opentelemetry.javaagent.instrumentation.jms.v1_1.JavaxMessageAdapter;
import javax.annotation.Nullable;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Instruments the simplified API's {@code JMSConsumer}, introduced in JMS 2.0.
 *
 * <p>Only the {@code Message}-returning receive methods are instrumented. {@code receiveBody}
 * unwraps the body inside the provider and never exposes the {@code Message}, so there is nothing
 * to extract trace context from at this boundary.
 *
 * <p>Durable and shared subscription names are not recorded for the simplified API: {@code
 * JmsSubscriptionNames} keys on {@code MessageConsumer}, and a {@code JMSConsumer} is not one.
 */
class JmsConsumerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("javax.jms.JMSConsumer");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("javax.jms.JMSConsumer"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("receive")
            .and(takesArguments(0).or(takesArguments(1)))
            .and(returns(named("javax.jms.Message")))
            .and(isPublic()),
        getClass().getName() + "$ConsumerAdvice");
    transformer.applyAdviceToMethod(
        named("receiveNoWait")
            .and(takesArguments(0))
            .and(returns(named("javax.jms.Message")))
            .and(isPublic()),
        getClass().getName() + "$ConsumerAdvice");
  }

  public static class AdviceScope {
    private final CallDepth callDepth;
    @Nullable private final Timer timer;

    private AdviceScope(CallDepth callDepth, @Nullable Timer timer) {
      this.callDepth = callDepth;
      this.timer = timer;
    }

    public static AdviceScope enter() {
      // deliberately keyed on MessageConsumer, not JMSConsumer: providers typically implement
      // JMSConsumer.receive by delegating to a MessageConsumer, and sharing the key means the
      // outermost receive wins and exactly one receive span is emitted
      CallDepth callDepth = CallDepth.forClass(MessageConsumer.class);
      if (callDepth.getAndIncrement() > 0) {
        return new AdviceScope(callDepth, null);
      }
      return new AdviceScope(callDepth, Timer.start());
    }

    public void exit(@Nullable Message message) {
      if (callDepth.decrementAndGet() > 0 || timer == null) {
        return;
      }
      if (message == null) {
        // Do not create span when no message is received
        return;
      }

      MessageWithDestination request =
          MessageWithDestination.create(JavaxMessageAdapter.create(message), null, null);

      createReceiveSpan(consumerReceiveInstrumenter(), request, timer, null);
    }
  }

  @SuppressWarnings("unused")
  public static class ConsumerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope onEnter() {
      return AdviceScope.enter();
    }

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void stopSpan(
        @Advice.Enter AdviceScope adviceScope, @Advice.Return @Nullable Message message) {
      adviceScope.exit(message);
    }
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.reactivestreams.Subscriber;

public class ExecutionBoundPublisherInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "ratpack.exec.internal.ExecutionBoundPublisher",
        "ratpack.exec.internal.DefaultExecution$ExecutionBoundPublisher");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("subscribe").and(takesArgument(0, named("org.reactivestreams.Subscriber"))),
        this.getClass().getName() + "$SubscribeAdvice");
  }

  @SuppressWarnings("unused")
  public static class SubscribeAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static <T> void wrap(
        @Advice.Argument(value = 0, readOnly = false) Subscriber<T> subscriber) {
      subscriber = new TracingSubscriber<>(subscriber, Java8BytecodeBridge.currentContext());
    }
  }
}

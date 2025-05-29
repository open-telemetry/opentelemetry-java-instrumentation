/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.armeria.v1_3;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.concurrent.CompletableFuture;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.reactivestreams.Subscriber;

public class AbstractStreamMessageSubscriptionInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "com.linecorp.armeria.common.stream.AbstractStreamMessage$SubscriptionImpl",
        // renamed in 1.19.0
        "com.linecorp.armeria.common.stream.CancellableStreamMessage$SubscriptionImpl");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(
                takesArgument(
                    0,
                    namedOneOf(
                        "com.linecorp.armeria.common.stream.AbstractStreamMessage",
                        "com.linecorp.armeria.common.stream.CancellableStreamMessage")))
            .and(takesArgument(1, named("org.reactivestreams.Subscriber"))),
        AbstractStreamMessageSubscriptionInstrumentation.class.getName() + "$WrapSubscriberAdvice");
    // from 1.9.0 to 1.9.2
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(
                takesArgument(0, named("com.linecorp.armeria.common.stream.AbstractStreamMessage")))
            .and(takesArgument(4, named("java.util.concurrent.CompletableFuture"))),
        AbstractStreamMessageSubscriptionInstrumentation.class.getName()
            + "$WrapCompletableFutureAdvice");
  }

  @SuppressWarnings("unused")
  public static class WrapSubscriberAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void wrapSubscriber(
        @Advice.Argument(value = 1, readOnly = false) Subscriber<?> subscriber) {
      subscriber = SubscriberWrapper.wrap(subscriber);
    }
  }

  @SuppressWarnings("unused")
  public static class WrapCompletableFutureAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void wrapCompletableFuture(
        @Advice.Argument(value = 4, readOnly = false) CompletableFuture<?> future) {
      future = CompletableFutureWrapper.wrap(future);
    }
  }
}

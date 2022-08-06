/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.armeria.v1_3;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
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
    return named("com.linecorp.armeria.common.stream.AbstractStreamMessage$SubscriptionImpl");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(
                takesArgument(0, named("com.linecorp.armeria.common.stream.AbstractStreamMessage")))
            .and(takesArgument(1, named("org.reactivestreams.Subscriber"))),
        AbstractStreamMessageSubscriptionInstrumentation.class.getName() + "$WrapSubscriberAdvice");
    // since 1.9.0
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(
                takesArgument(0, named("com.linecorp.armeria.common.stream.AbstractStreamMessage")))
            .and(takesArgument(4, named("java.util.concurrent.CompletableFuture"))),
        AbstractStreamMessageSubscriptionInstrumentation.class.getName()
            + "$WrapCompletableFutureAdvice");
  }

  @SuppressWarnings({"PrivateConstructorForUtilityClass", "unused"})
  public static class WrapSubscriberAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void wrapSubscriber(
        @Advice.Argument(value = 1, readOnly = false) Subscriber<?> subscriber) {
      subscriber = SubscriberWrapper.wrap(subscriber);
    }
  }

  public static class WrapCompletableFutureAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void wrapCompletableFuture(
        @Advice.Argument(value = 4, readOnly = false) CompletableFuture<?> future) {
      future = CompletableFutureWrapper.wrap(future);
    }
  }
}

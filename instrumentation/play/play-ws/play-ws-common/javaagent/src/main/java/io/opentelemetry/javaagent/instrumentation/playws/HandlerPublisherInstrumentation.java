/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.playws;

import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.reactivestreams.Subscriber;

public class HandlerPublisherInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("play.shaded.ahc.com.typesafe.netty.HandlerPublisher");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("subscribe"),
        HandlerPublisherInstrumentation.class.getName() + "$WrapSubscriberAdvice");
  }

  @SuppressWarnings("unused")
  public static class WrapSubscriberAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void enter(
        @Advice.Argument(value = 0, readOnly = false) Subscriber<?> subscriber) {
      subscriber = new SubscriberWrapper<>(subscriber, Java8BytecodeBridge.currentContext());
    }
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpclient;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.httpclient.BodyHandlerWrapper.BodySubscriberWrapper;
import java.net.http.HttpResponse;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class TrustedSubscriberInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("jdk.internal.net.http.ResponseSubscribers$TrustedSubscriber");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("needsExecutor")
            .and(takesArgument(0, named("java.net.http.HttpResponse$BodySubscriber"))),
        TrustedSubscriberInstrumentation.class.getName() + "$NeedsExecutorAdvice");
  }

  @SuppressWarnings("unused")
  public static class NeedsExecutorAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Argument(value = 0, readOnly = false)
            HttpResponse.BodySubscriber<?> bodySubscriber) {
      if (bodySubscriber instanceof BodySubscriberWrapper) {
        bodySubscriber = ((BodySubscriberWrapper<?>) bodySubscriber).getDelegate();
      }
    }
  }
}

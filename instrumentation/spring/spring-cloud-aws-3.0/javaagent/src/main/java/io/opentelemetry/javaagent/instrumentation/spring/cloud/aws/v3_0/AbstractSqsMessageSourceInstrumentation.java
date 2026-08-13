/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.cloud.aws.v3_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.bootstrap.InternalListenerPollContext;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class AbstractSqsMessageSourceInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("io.awspring.cloud.sqs.listener.source.AbstractSqsMessageSource");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.awspring.cloud.sqs.listener.source.AbstractSqsMessageSource");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("doPollForMessages").and(takesArguments(1)), getClass().getName() + "$PollAdvice");
  }

  @SuppressWarnings("unused")
  public static class PollAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static boolean onEnter() {
      return InternalListenerPollContext.enter();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.Enter boolean wasActive) {
      InternalListenerPollContext.exit(wasActive);
    }
  }
}

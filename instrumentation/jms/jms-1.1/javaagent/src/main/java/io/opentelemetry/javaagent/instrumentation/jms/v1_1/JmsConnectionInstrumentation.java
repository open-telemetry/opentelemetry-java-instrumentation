/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v1_1;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import javax.jms.Connection;
import javax.jms.Session;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class JmsConnectionInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("javax.jms.Connection");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("javax.jms.Connection"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        namedOneOf(
                "createSession",
                "createQueueSession",
                "createTopicSession",
                "createXASession",
                "createXAQueueSession",
                "createXATopicSession")
            .and(isPublic()),
        getClass().getName() + "$CreateSessionAdvice");
    transformer.applyAdviceToMethod(
        named("close").and(takesArguments(0)).and(isPublic()),
        getClass().getName() + "$CloseAdvice");
  }

  @SuppressWarnings("unused")
  public static class CreateSessionAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This Connection connection, @Advice.Return @Nullable Session session) {
      if (session != null) {
        JmsSubscriptionNames.setConnection(session, connection);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class CloseAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.This Connection connection) {
      JmsSubscriptionNames.clearConnection(connection);
    }
  }
}

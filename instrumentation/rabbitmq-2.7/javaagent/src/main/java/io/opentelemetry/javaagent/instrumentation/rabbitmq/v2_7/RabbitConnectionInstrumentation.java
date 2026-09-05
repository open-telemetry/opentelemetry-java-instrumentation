/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

import com.rabbitmq.client.Connection;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class RabbitConnectionInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("com.rabbitmq.client.impl.AMQConnection");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // RecoveryAwareAMQConnection inherits start() rather than overriding it, so advising the
    // declaring class covers the recovering connections too
    return named("com.rabbitmq.client.impl.AMQConnection");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(named("start")).and(takesNoArguments()),
        getClass().getName() + "$StartAdvice");
  }

  @SuppressWarnings("unused")
  public static class StartAdvice {

    /**
     * Reads the private {@code _virtualHost} field, which has carried that name since amqp-client
     * 2.7.0. Muzzle cannot see field references bound by {@code @Advice.FieldValue}, so a rename
     * would only be caught by the testLatestDeps build, not by a muzzle check.
     */
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This Connection connection,
        @Advice.FieldValue("_virtualHost") @Nullable String virtualHost) {
      if (virtualHost != null) {
        RabbitConnectionAttributes.VIRTUAL_HOST.set(connection, virtualHost);
      }
    }
  }
}

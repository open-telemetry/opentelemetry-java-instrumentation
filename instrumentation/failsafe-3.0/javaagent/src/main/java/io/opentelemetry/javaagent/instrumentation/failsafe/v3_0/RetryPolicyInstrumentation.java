/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.failsafe.v3_0;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

import dev.failsafe.PolicyConfig;
import dev.failsafe.internal.RetryPolicyImpl;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.failsafe.v3_0.FailsafeTelemetry;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.lang.reflect.Field;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public final class RetryPolicyInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("dev.failsafe.RetryPolicyBuilder");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("build").and(takesNoArguments()), this.getClass().getName() + "$BuildAdvice");
  }

  public static final class BuildAdvice {
    @Nullable public static final Field FAILURE_LISTENER_FIELD;
    @Nullable public static final Field SUCCESS_LISTENER_FIELD;

    static {
      Field failureListenerField = null;
      Field successListenerField = null;
      try {
        failureListenerField = PolicyConfig.class.getDeclaredField("failureListener");
        failureListenerField.setAccessible(true);

        successListenerField = PolicyConfig.class.getDeclaredField("successListener");
        successListenerField.setAccessible(true);
      } catch (Exception e) {
        // Ignored
      }
      FAILURE_LISTENER_FIELD = failureListenerField;
      SUCCESS_LISTENER_FIELD = successListenerField;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.Return Object retryPolicyImpl) throws IllegalAccessException {
      if (FAILURE_LISTENER_FIELD == null || SUCCESS_LISTENER_FIELD == null) {
        return;
      }

      RetryPolicyImpl<?> impl = (RetryPolicyImpl<?>) retryPolicyImpl;
      FailsafeTelemetry failsafeTelemetry = FailsafeTelemetry.create(GlobalOpenTelemetry.get());

      FAILURE_LISTENER_FIELD.set(
          impl.getConfig(),
          failsafeTelemetry.createInstrumentedFailureListener(impl.getConfig(), impl.toString()));
      SUCCESS_LISTENER_FIELD.set(
          impl.getConfig(),
          failsafeTelemetry.createInstrumentedSuccessListener(impl.getConfig(), impl.toString()));
    }
  }
}

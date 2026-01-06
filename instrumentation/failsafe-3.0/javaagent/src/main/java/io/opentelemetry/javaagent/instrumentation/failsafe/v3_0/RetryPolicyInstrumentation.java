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
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

final class RetryPolicyInstrumentation implements TypeInstrumentation {
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
    @Advice.OnMethodExit
    public static void onExit(@Advice.Return Object retryPolicyImpl)
        throws NoSuchFieldException, IllegalAccessException {
      RetryPolicyImpl<?> impl = (RetryPolicyImpl<?>) retryPolicyImpl;
      FailsafeTelemetry failsafeTelemetry = FailsafeTelemetry.create(GlobalOpenTelemetry.get());

      Field failureListenerField = PolicyConfig.class.getDeclaredField("failureListener");
      failureListenerField.setAccessible(true);
      failureListenerField.set(
          impl.getConfig(),
          failsafeTelemetry.createInstrumentedFailureListener(impl.getConfig(), impl.toString()));

      Field successListenerField = PolicyConfig.class.getDeclaredField("successListener");
      successListenerField.setAccessible(true);
      successListenerField.set(
          impl.getConfig(),
          failsafeTelemetry.createInstrumentedSuccessListener(impl.getConfig(), impl.toString()));
    }
  }
}

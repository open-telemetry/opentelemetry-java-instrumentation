/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.axis2;

import static net.bytebuddy.matcher.ElementMatchers.isTypeInitializer;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.axis2.jaxws.registry.InvocationListenerRegistry;

public class InvocationListenerRegistryTypeInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.axis2.jaxws.registry.InvocationListenerRegistry");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isTypeInitializer(),
        InvocationListenerRegistryTypeInstrumentation.class.getName() + "$ClassInitializerAdvice");
  }

  @SuppressWarnings("unused")
  public static class ClassInitializerAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit() {
      InvocationListenerRegistry.addFactory(new TracingInvocationListenerFactory());
    }
  }
}

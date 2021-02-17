/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.axis2;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isTypeInitializer;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.instrumentation.axis2.TracingInvocationListenerFactory;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.axis2.jaxws.registry.InvocationListenerRegistry;

public class InvocationListenerRegistryTypeInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("org.apache.axis2.jaxws.registry.InvocationListenerRegistry");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isTypeInitializer(),
        InvocationListenerRegistryTypeInstrumentation.class.getName() + "$ClassInitializerAdvice");
  }

  public static class ClassInitializerAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit() {
      InvocationListenerRegistry.addFactory(new TracingInvocationListenerFactory());
    }
  }
}

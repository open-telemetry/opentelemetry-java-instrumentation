/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import application.io.opentelemetry.context.Context;
import application.io.opentelemetry.context.ContextStorage;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.AgentContextStorage;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Collections;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Returns {@link AgentContextStorage} as the implementation of {@link ContextStorage} in the
 * application classpath. We do this instead of using the normal service loader mechanism to make
 * sure there is no dependency on a system property or possibility of a user overriding this since
 * it's required for instrumentation in the agent to work properly.
 */
public class ContextInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("application.io.opentelemetry.context.ArrayBasedContext");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return Collections.singletonMap(
        isMethod().and(isStatic()).and(named("root")),
        ContextInstrumentation.class.getName() + "$GetAdvice");
  }

  public static class GetAdvice {
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(@Advice.Return(readOnly = false) Context root) {
      root =
          new AgentContextStorage.AgentContextWrapper(
              io.opentelemetry.context.Context.root(), root);
    }
  }
}

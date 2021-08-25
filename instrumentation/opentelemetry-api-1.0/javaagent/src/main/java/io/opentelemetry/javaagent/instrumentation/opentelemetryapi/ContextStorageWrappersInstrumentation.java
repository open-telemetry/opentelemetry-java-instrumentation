/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi;

import static net.bytebuddy.matcher.ElementMatchers.named;

import application.io.opentelemetry.context.ContextStorage;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.AgentContextStorage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Returns {@link AgentContextStorage} as the implementation of {@link ContextStorage} in the
 * application classpath. We do this instead of using the normal service loader mechanism to make
 * sure there is no dependency on a system property or possibility of a user overriding this since
 * it's required for instrumentation in the agent to work properly.
 */
public class ContextStorageWrappersInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("application.io.opentelemetry.context.ContextStorageWrappers");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("getWrappers"),
        ContextStorageWrappersInstrumentation.class.getName() + "$AddWrapperAdvice");
  }

  @SuppressWarnings("unused")
  public static class AddWrapperAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Return(readOnly = false)
            List<Function<? super ContextStorage, ? extends ContextStorage>> wrappers) {
      wrappers = new ArrayList<>(wrappers);
      wrappers.add(AgentContextStorage.wrap());
    }
  }
}

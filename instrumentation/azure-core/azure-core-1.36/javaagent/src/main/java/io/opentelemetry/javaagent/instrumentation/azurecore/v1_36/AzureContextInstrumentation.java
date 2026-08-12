/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.azurecore.v1_36;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Optional;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Bridges an explicitly supplied parent context for {@code azure-core-tracing-opentelemetry}.
 *
 * <p>When a user passes a parent context to an Azure SDK call, the value is stored on the {@link
 * com.azure.core.util.Context} under {@link
 * com.azure.core.util.tracing.Tracer#PARENT_TRACE_CONTEXT_KEY} as the application's (unshaded)
 * {@code io.opentelemetry.context.Context}. The bundled {@code OpenTelemetryTracer} reads that
 * value back and expects it to be the agent's (shaded) context. Convert it here so the tracer does
 * not need to reach into agent internals reflectively.
 */
class AzureContextInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.azure.core.util.Context");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("getData").and(takesArguments(1)), getClass().getName() + "$GetDataAdvice");
  }

  @SuppressWarnings("unused")
  public static class GetDataAdvice {
    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static Optional<Object> onExit(
        @Advice.Argument(0) Object key, @Advice.Return Optional<Object> data) {
      return AzureExplicitParentContextHelper.bridgeApplicationContext(key, data);
    }
  }
}

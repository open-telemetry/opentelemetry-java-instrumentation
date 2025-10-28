/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import kotlin.coroutines.CoroutineContext;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class KotlinCoroutinesInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("kotlinx.coroutines.CoroutineContextKt");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("newCoroutineContext")
            .and(takesArgument(0, named("kotlinx.coroutines.CoroutineScope")))
            .and(takesArgument(1, named("kotlin.coroutines.CoroutineContext"))),
        this.getClass().getName() + "$ContextAdvice");
  }

  @SuppressWarnings("unused")
  public static class ContextAdvice {

    @AssignReturned.ToArguments(@ToArgument(1))
    @Advice.OnMethodEnter
    public static CoroutineContext enter(@Advice.Argument(1) CoroutineContext coroutineContext) {
      return coroutineContext == null
          ? null
          : KotlinCoroutinesInstrumentationHelper.addOpenTelemetryContext(coroutineContext);
    }
  }
}

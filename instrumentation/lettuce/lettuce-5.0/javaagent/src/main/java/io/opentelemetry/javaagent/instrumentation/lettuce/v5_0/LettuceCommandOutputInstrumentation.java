/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.lettuce.core.output.CommandOutput;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class LettuceCommandOutputInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.lettuce.core.output.CommandOutput");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("setError").and(takesArguments(String.class)),
        getClass().getName() + "$SetErrorAdvice");
  }

  @SuppressWarnings("unused")
  public static class SetErrorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.This CommandOutput<?, ?, ?> output) {
      // MultiOutput forwards decoded transaction errors to individual command outputs.
      LettuceRedisErrorType.captureDecoded(output);
    }
  }
}

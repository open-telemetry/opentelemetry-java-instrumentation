/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.lettuce.core.output.CommandOutput;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.nio.ByteBuffer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class LettuceRedisStateMachineInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.lettuce.core.protocol.RedisStateMachine");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("safeSetError").and(takesArguments(3)).and(takesArgument(1, ByteBuffer.class)),
        getClass().getName() + "$DecodeErrorAdvice");
  }

  @SuppressWarnings("unused")
  public static class DecodeErrorAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static LettuceRedisErrorType.ErrorDecode onEnter(
        @Advice.Argument(1) @Nullable ByteBuffer bytes) {
      return LettuceRedisErrorType.startErrorDecode(bytes);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Argument(0) CommandOutput<?, ?, ?> output,
        @Advice.Enter @Nullable LettuceRedisErrorType.ErrorDecode decode) {
      LettuceRedisErrorType.endErrorDecode(output, decode);
    }
  }
}

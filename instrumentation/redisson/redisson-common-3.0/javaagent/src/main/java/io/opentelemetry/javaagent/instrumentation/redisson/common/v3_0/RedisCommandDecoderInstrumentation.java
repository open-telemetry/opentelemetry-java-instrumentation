/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0;

import static io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0.RedissonErrorCapture.currentServerErrorType;
import static io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0.RedissonErrorCapture.parseServerErrorType;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.netty.buffer.ByteBuf;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class RedisCommandDecoderInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.redisson.client.handler.CommandDecoder");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("decode")
            .and(takesArgument(0, named("io.netty.buffer.ByteBuf")))
            .and(takesArgument(1, named("org.redisson.client.protocol.CommandData"))),
        getClass().getName() + "$DecodeAdvice");
  }

  @SuppressWarnings("unused")
  public static class DecodeAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static String onEnter(@Advice.Argument(0) ByteBuf input) {
      return currentServerErrorType().set(parseServerErrorType(input));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable String previousErrorType) {
      currentServerErrorType().restore(previousErrorType);
    }
  }
}

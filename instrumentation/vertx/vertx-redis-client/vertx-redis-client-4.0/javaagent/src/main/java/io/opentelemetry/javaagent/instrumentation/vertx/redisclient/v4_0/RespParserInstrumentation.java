/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_0;

import static io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_0.VertxRedisClientSingletons.currentErrorParser;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.redis.client.Response;
import io.vertx.redis.client.impl.RESPParser;
import io.vertx.redis.client.impl.types.ErrorType;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class RespParserInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.vertx.redis.client.impl.RESPParser");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("handleError").and(takesArguments(int.class)), getClass().getName() + "$ErrorAdvice");
    transformer.applyAdviceToMethod(
        named("handleResponse")
            .and(takesArgument(0, named("io.vertx.redis.client.Response")))
            .and(takesArgument(1, boolean.class)),
        getClass().getName() + "$ResponseAdvice");
  }

  @SuppressWarnings("unused")
  public static class ErrorAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static RESPParser onEnter(@Advice.This RESPParser parser) {
      return currentErrorParser().set(parser);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable RESPParser previous) {
      currentErrorParser().restore(previous);
    }
  }

  @SuppressWarnings("unused")
  public static class ResponseAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(
        @Advice.This RESPParser parser, @Advice.Argument(0) @Nullable Response response) {
      if (currentErrorParser().get() == parser && response instanceof ErrorType) {
        // readLine has already removed the RESP terminator; local parser failures bypass this path.
        VertxRedisClientError.captureServerError((ErrorType) response);
      }
    }
  }
}

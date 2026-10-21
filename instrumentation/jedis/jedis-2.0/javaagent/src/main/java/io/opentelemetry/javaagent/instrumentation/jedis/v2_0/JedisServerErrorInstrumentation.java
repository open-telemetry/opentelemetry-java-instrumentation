/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.jedis.common.JedisRequestContext;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class JedisServerErrorInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf("redis.clients.jedis.Protocol", "redis.clients.util.RedisInputStream");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("processError"), getClass().getName() + "$ProcessErrorAdvice");
    transformer.applyAdviceToMethod(named("readLine"), getClass().getName() + "$ReadLineAdvice");
  }

  @SuppressWarnings("unused")
  public static class ProcessErrorAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static boolean onEnter() {
      return JedisRequestContext.beginServerErrorReply();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Enter boolean previous, @Advice.Thrown @Nullable Throwable throwable) {
      JedisRequestContext.endServerErrorReply(previous, throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class ReadLineAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Return @Nullable String reply) {
      if (reply != null) {
        JedisRequestContext.captureServerErrorReply(reply);
      }
    }
  }
}

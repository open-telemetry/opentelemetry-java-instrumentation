/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import static io.opentelemetry.javaagent.instrumentation.jedis.v4_0.JedisSingletons.transactionFramingSuppression;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.jedis.common.JedisRequestContext;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class JedisInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf("redis.clients.jedis.Jedis", "redis.clients.jedis.UnifiedJedis");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isPublic()
            .and(isMethod())
            .and(not(isStatic()))
            .and(
                not(
                    namedOneOf(
                        "close",
                        "setDataSource",
                        "getDB",
                        "isConnected",
                        "connect",
                        "resetState",
                        "getClient",
                        "disconnect",
                        "getConnection",
                        "isBroken",
                        "multi",
                        "toString"))),
        getClass().getName() + "$JedisMethodAdvice");
    transformer.applyAdviceToMethod(
        named("multi").and(takesArguments(0)), getClass().getName() + "$MultiAdvice");
  }

  @SuppressWarnings("unused")
  public static class JedisMethodAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static JedisRequestContext<JedisRequest> onEnter() {
      return JedisRequestContext.attach();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable JedisRequestContext<JedisRequest> requestContext) {
      if (requestContext != null) {
        requestContext.detachAndEnd(throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class MultiAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static boolean onEnter() {
      // The MULTI command frames a transaction that is reported as a single batch span at exec(),
      // so its own command span is suppressed.
      return transactionFramingSuppression().tryAcquire();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter boolean acquired) {
      if (acquired) {
        transactionFramingSuppression().release();
      }
    }
  }
}

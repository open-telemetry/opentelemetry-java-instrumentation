/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v1_4;

import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.not;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.jedis.JedisRequestContext;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class JedisInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf("redis.clients.jedis.Jedis", "redis.clients.jedis.BinaryJedis");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isPublic()
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
                        "disconnect"))),
        this.getClass().getName() + "$JedisMethodAdvice");
  }

  @SuppressWarnings("unused")
  public static class JedisMethodAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static JedisRequestContext<JedisRequest> onEnter() {
      return JedisRequestContext.attach();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Enter @Nullable JedisRequestContext<JedisRequest> requestContext) {
      if (requestContext != null) {
        requestContext.detachAndEnd();
      }
    }
  }
}

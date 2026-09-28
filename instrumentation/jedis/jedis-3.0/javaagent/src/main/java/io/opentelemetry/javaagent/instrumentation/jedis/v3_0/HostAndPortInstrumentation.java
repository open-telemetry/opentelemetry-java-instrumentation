/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import redis.clients.jedis.HostAndPort;

class HostAndPortInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("redis.clients.jedis.HostAndPort");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("parseString")
            .and(isStatic())
            .and(takesArgument(0, String.class))
            .and(returns(named("redis.clients.jedis.HostAndPort"))),
        getClass().getName() + "$ParseStringAdvice");
  }

  @SuppressWarnings("unused")
  public static class ParseStringAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Argument(0) @Nullable String configuredEndpoint,
        @Advice.Return @Nullable HostAndPort parsedEndpoint) {
      JedisConfiguredTargets.captureOriginalEndpoint(parsedEndpoint, configuredEndpoint);
    }
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v4_0.LettuceConnectionDatabaseClientTracer.tracer;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.lambdaworks.redis.RedisURI;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class LettuceClientInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.lambdaworks.redis.RedisClient");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod().and(named("connectStandalone")),
        LettuceClientInstrumentation.class.getName() + "$ConnectAdvice");
  }

  public static class ConnectAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(1) RedisURI redisUri,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      context = tracer().startSpan(currentContext(), redisUri, "CONNECT");
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      scope.close();
      if (throwable != null) {
        tracer().endExceptionally(context, throwable);
      } else {
        tracer().end(context);
      }
    }
  }
}

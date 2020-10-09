/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.lettuce.v4_0;

import com.lambdaworks.redis.RedisURI;
import io.opentelemetry.javaagent.instrumentation.api.SpanWithScope;
import net.bytebuddy.asm.Advice;

public class RedisConnectionAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static SpanWithScope onEnter(@Advice.Argument(1) RedisURI redisURI) {
    return InstrumentationPoints.beforeConnect(redisURI);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void onExit(@Advice.Enter SpanWithScope scope, @Advice.Thrown Throwable throwable) {
    InstrumentationPoints.afterConnect(scope, throwable);
  }
}

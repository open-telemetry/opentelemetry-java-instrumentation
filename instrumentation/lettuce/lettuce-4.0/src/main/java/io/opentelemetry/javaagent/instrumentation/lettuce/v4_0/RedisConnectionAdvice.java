/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import com.lambdaworks.redis.RedisURI;
import io.opentelemetry.javaagent.instrumentation.api.SpanWithScope;
import net.bytebuddy.asm.Advice;

public class RedisConnectionAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static SpanWithScope onEnter(@Advice.Argument(1) RedisURI redisUri) {
    return InstrumentationPoints.beforeConnect(redisUri);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void onExit(@Advice.Enter SpanWithScope scope, @Advice.Thrown Throwable throwable) {
    InstrumentationPoints.afterConnect(scope, throwable);
  }
}

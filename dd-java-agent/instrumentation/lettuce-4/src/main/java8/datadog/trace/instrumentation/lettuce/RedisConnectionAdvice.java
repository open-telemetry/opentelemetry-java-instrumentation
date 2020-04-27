package datadog.trace.instrumentation.lettuce;

import com.lambdaworks.redis.RedisURI;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import net.bytebuddy.asm.Advice;

public class RedisConnectionAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static AgentScope onEnter(@Advice.Argument(1) final RedisURI redisURI) {
    return InstrumentationPoints.beforeConnect(redisURI);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void onExit(@Advice.Enter final AgentScope scope,
                            @Advice.Thrown final Throwable throwable) {
    InstrumentationPoints.afterConnect(scope, throwable);
  }
}

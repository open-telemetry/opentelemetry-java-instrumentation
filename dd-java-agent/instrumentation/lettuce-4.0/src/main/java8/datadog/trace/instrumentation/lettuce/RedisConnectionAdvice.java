package datadog.trace.instrumentation.lettuce;

import com.lambdaworks.redis.RedisURI;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import net.bytebuddy.asm.Advice;

public class RedisConnectionAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static AgentScope onEnter(@Advice.Argument(1) RedisURI redisURI) {
    return InstrumentationPoints.onEnter(redisURI);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void onReturn(@Advice.Enter AgentScope scope, @Advice.Thrown Throwable throwable) {
    InstrumentationPoints.onReturn(scope, throwable);
  }
}

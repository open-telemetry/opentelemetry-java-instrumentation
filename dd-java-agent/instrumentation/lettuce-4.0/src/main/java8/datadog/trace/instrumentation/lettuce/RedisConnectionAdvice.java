package datadog.trace.instrumentation.lettuce;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.lettuce.LettuceClientDecorator.DECORATE;

import com.lambdaworks.redis.RedisURI;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import net.bytebuddy.asm.Advice;

public class RedisConnectionAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static AgentScope onEnter(@Advice.Argument(1) RedisURI redisURI) {
    AgentSpan span = startSpan("redis.query");
    DECORATE.afterStart(span);
    DECORATE.onConnection(span, redisURI);
    return activateSpan(span, false);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void onReturn(@Advice.Enter AgentScope scope, @Advice.Thrown Throwable throwable) {
    AgentSpan span = scope.span();
    try {
      if (throwable != null) {
        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(span);
      }
    } finally {
      span.finish();
      scope.close();
    }
  }
}

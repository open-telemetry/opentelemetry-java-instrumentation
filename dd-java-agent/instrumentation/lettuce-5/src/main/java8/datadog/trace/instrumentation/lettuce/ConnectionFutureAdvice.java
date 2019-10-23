package datadog.trace.instrumentation.lettuce;

import static datadog.trace.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.lettuce.LettuceClientDecorator.DECORATE;

import datadog.trace.instrumentation.api.AgentScope;
import datadog.trace.instrumentation.api.AgentSpan;
import io.lettuce.core.ConnectionFuture;
import io.lettuce.core.RedisURI;
import net.bytebuddy.asm.Advice;

public class ConnectionFutureAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static AgentScope onEnter(@Advice.Argument(1) final RedisURI redisURI) {
    final AgentSpan span = startSpan("redis.query");
    DECORATE.afterStart(span);
    DECORATE.onConnection(span, redisURI);
    return activateSpan(span, false);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Enter final AgentScope scope,
      @Advice.Thrown final Throwable throwable,
      @Advice.Return final ConnectionFuture<?> connectionFuture) {
    final AgentSpan span = scope.span();
    if (throwable != null) {
      DECORATE.onError(span, throwable);
      DECORATE.beforeFinish(span);
      span.finish();
      scope.close();
      return;
    }
    connectionFuture.handleAsync(new LettuceAsyncBiFunction<>(span));
    scope.close();
  }
}

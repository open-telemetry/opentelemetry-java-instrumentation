package datadog.trace.instrumentation.lettuce;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.lettuce.LettuceClientDecorator.DECORATE;
import static datadog.trace.instrumentation.lettuce.LettuceInstrumentationUtil.finishSpanEarly;

import com.lambdaworks.redis.RedisURI;
import com.lambdaworks.redis.protocol.AsyncCommand;
import com.lambdaworks.redis.protocol.RedisCommand;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;

@SuppressWarnings("rawtypes")
public final class InstrumentationPoints {

  public static AgentScope onEnter(RedisCommand command) {
    AgentSpan span = startSpan("redis.query");
    DECORATE.afterStart(span);
    DECORATE.onCommand(span, command);
    return activateSpan(span, finishSpanEarly(command));
  }

  public static void onReturn(RedisCommand command,
                              AgentScope scope,
                              Throwable throwable,
                              AsyncCommand<?, ?, ?> asyncCommand) {
      AgentSpan span = scope.span();
      if (throwable != null) {
        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(span);
        span.finish();
      } else if (!finishSpanEarly(command)) {
        asyncCommand.handleAsync(new LettuceAsyncBiFunction<>(span));
      }
      scope.close();
  }

  public static AgentScope onEnter(RedisURI redisURI) {
    AgentSpan span = startSpan("redis.query");
    DECORATE.afterStart(span);
    DECORATE.onConnection(span, redisURI);
    return activateSpan(span, false);
  }

  public static void onReturn(AgentScope scope, Throwable throwable) {
    AgentSpan span = scope.span();
    if (throwable != null) {
      DECORATE.onError(span, throwable);
      DECORATE.beforeFinish(span);
    }
    span.finish();
    scope.close();
  }
}

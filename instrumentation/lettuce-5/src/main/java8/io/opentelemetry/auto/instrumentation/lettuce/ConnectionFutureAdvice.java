package io.opentelemetry.auto.instrumentation.lettuce;

import static io.opentelemetry.auto.instrumentation.lettuce.LettuceClientDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.lettuce.LettuceClientDecorator.TRACER;

import io.lettuce.core.ConnectionFuture;
import io.lettuce.core.RedisURI;
import io.opentelemetry.auto.instrumentation.api.SpanScopePair;
import io.opentelemetry.trace.Span;
import net.bytebuddy.asm.Advice;

public class ConnectionFutureAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static SpanScopePair onEnter(@Advice.Argument(1) final RedisURI redisURI) {
    final Span span = TRACER.spanBuilder("redis.query").startSpan();
    DECORATE.afterStart(span);
    DECORATE.onConnection(span, redisURI);
    return new SpanScopePair(span, TRACER.withSpan(span));
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Enter final SpanScopePair spanScopePair,
      @Advice.Thrown final Throwable throwable,
      @Advice.Return final ConnectionFuture<?> connectionFuture) {
    final Span span = spanScopePair.getSpan();
    if (throwable != null) {
      DECORATE.onError(span, throwable);
      DECORATE.beforeFinish(span);
      span.end();
      spanScopePair.getScope().close();
      return;
    }
    connectionFuture.handleAsync(new LettuceAsyncBiFunction<>(span));
    spanScopePair.getScope().close();
  }
}

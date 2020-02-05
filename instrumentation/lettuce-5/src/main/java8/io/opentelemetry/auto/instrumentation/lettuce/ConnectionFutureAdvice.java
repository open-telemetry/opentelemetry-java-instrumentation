package io.opentelemetry.auto.instrumentation.lettuce;

import static io.opentelemetry.auto.instrumentation.lettuce.LettuceClientDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.lettuce.LettuceClientDecorator.TRACER;

import io.lettuce.core.ConnectionFuture;
import io.lettuce.core.RedisURI;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.trace.Span;
import net.bytebuddy.asm.Advice;

public class ConnectionFutureAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static SpanWithScope onEnter(@Advice.Argument(1) final RedisURI redisURI) {
    final Span span = TRACER.spanBuilder("redis.query").startSpan();
    DECORATE.afterStart(span);
    DECORATE.onConnection(span, redisURI);
    return new SpanWithScope(span, TRACER.withSpan(span));
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Enter final SpanWithScope spanWithScope,
      @Advice.Thrown final Throwable throwable,
      @Advice.Return final ConnectionFuture<?> connectionFuture) {
    final Span span = spanWithScope.getSpan();
    if (throwable != null) {
      DECORATE.onError(span, throwable);
      DECORATE.beforeFinish(span);
      span.end();
      spanWithScope.closeScope();
      return;
    }
    connectionFuture.handleAsync(new LettuceAsyncBiFunction<>(span));
    spanWithScope.closeScope();
  }
}

package datadog.trace.instrumentation.lettuce;

import static datadog.trace.instrumentation.lettuce.LettuceClientDecorator.DECORATE;

import io.lettuce.core.ConnectionFuture;
import io.lettuce.core.RedisURI;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;
import net.bytebuddy.asm.Advice;

public class ConnectionFutureAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static Scope startSpan(@Advice.Argument(1) final RedisURI redisURI) {
    final Scope scope = GlobalTracer.get().buildSpan("redis.query").startActive(false);
    final Span span = scope.span();
    DECORATE.afterStart(span);
    DECORATE.onConnection(span, redisURI);
    return scope;
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Enter final Scope scope,
      @Advice.Thrown final Throwable throwable,
      @Advice.Return final ConnectionFuture<?> connectionFuture) {
    final Span span = scope.span();
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

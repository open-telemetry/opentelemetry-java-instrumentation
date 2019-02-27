package datadog.trace.instrumentation.lettuce;

import static datadog.trace.instrumentation.lettuce.LettuceClientDecorator.DECORATE;
import static datadog.trace.instrumentation.lettuce.LettuceInstrumentationUtil.doFinishSpanEarly;

import io.lettuce.core.protocol.AsyncCommand;
import io.lettuce.core.protocol.RedisCommand;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;
import net.bytebuddy.asm.Advice;

public class LettuceAsyncCommandsAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static Scope startSpan(@Advice.Argument(0) final RedisCommand command) {

    final Scope scope =
        GlobalTracer.get().buildSpan("redis.query").startActive(doFinishSpanEarly(command));

    final Span span = scope.span();
    DECORATE.afterStart(span);
    DECORATE.onCommand(span, command);
    return scope;
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(0) final RedisCommand command,
      @Advice.Enter final Scope scope,
      @Advice.Thrown final Throwable throwable,
      @Advice.Return final AsyncCommand<?, ?, ?> asyncCommand) {

    final Span span = scope.span();
    if (throwable != null) {
      DECORATE.onError(span, throwable);
      DECORATE.beforeFinish(span);
      span.finish();
      scope.close();
      return;
    }

    // close spans on error or normal completion
    if (!doFinishSpanEarly(command)) {
      asyncCommand.handleAsync(new LettuceAsyncBiFunction<>(span));
    }
    scope.close();
  }
}

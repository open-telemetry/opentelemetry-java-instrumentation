package io.opentelemetry.auto.instrumentation.lettuce;

import static io.opentelemetry.auto.instrumentation.lettuce.LettuceClientDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.lettuce.LettuceClientDecorator.TRACER;
import static io.opentelemetry.auto.instrumentation.lettuce.LettuceInstrumentationUtil.doFinishSpanEarly;
import static io.opentelemetry.trace.Span.Kind.CLIENT;

import io.lettuce.core.protocol.AsyncCommand;
import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.trace.Span;
import net.bytebuddy.asm.Advice;

public class LettuceAsyncCommandsAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static SpanWithScope onEnter(@Advice.Argument(0) final RedisCommand command) {

    final Span span = TRACER.spanBuilder("redis.query").setSpanKind(CLIENT).startSpan();
    DECORATE.afterStart(span);
    DECORATE.onCommand(span, command);

    return new SpanWithScope(span, TRACER.withSpan(span));
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(0) final RedisCommand command,
      @Advice.Enter final SpanWithScope spanWithScope,
      @Advice.Thrown final Throwable throwable,
      @Advice.Return final AsyncCommand<?, ?, ?> asyncCommand) {

    final Span span = spanWithScope.getSpan();
    if (throwable != null) {
      DECORATE.onError(span, throwable);
      DECORATE.beforeFinish(span);
      span.end();
      spanWithScope.closeScope();
      return;
    }

    // close spans on error or normal completion
    if (doFinishSpanEarly(command)) {
      span.end();
    } else {
      asyncCommand.handleAsync(new LettuceAsyncBiFunction<>(span));
    }
    spanWithScope.closeScope();
  }
}

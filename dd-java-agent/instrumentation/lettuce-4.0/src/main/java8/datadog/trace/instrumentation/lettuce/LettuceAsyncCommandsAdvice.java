package datadog.trace.instrumentation.lettuce;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.lettuce.LettuceClientDecorator.DECORATE;
import static datadog.trace.instrumentation.lettuce.LettuceInstrumentationUtil.doFinishSpanEarly;

import com.lambdaworks.redis.protocol.AsyncCommand;
import com.lambdaworks.redis.protocol.RedisCommand;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import net.bytebuddy.asm.Advice;

public class LettuceAsyncCommandsAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static AgentScope onEnter(@Advice.Argument(0) final RedisCommand command) {
    final AgentSpan span = startSpan("redis.query");
    DECORATE.afterStart(span);
    DECORATE.onCommand(span, command);
    return activateSpan(span, doFinishSpanEarly(command));
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(0) final RedisCommand command,
      @Advice.Enter final AgentScope scope,
      @Advice.Thrown final Throwable throwable,
      @Advice.Return final AsyncCommand<?, ?, ?> asyncCommand) {
    final AgentSpan span = scope.span();
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

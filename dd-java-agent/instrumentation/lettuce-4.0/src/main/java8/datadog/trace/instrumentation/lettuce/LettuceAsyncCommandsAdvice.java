package datadog.trace.instrumentation.lettuce;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;

import com.lambdaworks.redis.protocol.AsyncCommand;
import com.lambdaworks.redis.protocol.RedisCommand;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import net.bytebuddy.asm.Advice;

public class LettuceAsyncCommandsAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static AgentScope onEnter(@Advice.Argument(0) final RedisCommand command) {
    return InstrumentationPoints.onEnter(command);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(0) RedisCommand command,
      @Advice.Enter AgentScope scope,
      @Advice.Thrown Throwable throwable,
      @Advice.Return AsyncCommand<?, ?, ?> asyncCommand) {
    InstrumentationPoints.onReturn(command, scope, throwable, asyncCommand);
  }

}

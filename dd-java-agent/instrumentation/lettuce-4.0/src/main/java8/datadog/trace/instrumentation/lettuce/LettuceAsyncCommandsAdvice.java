package datadog.trace.instrumentation.lettuce;

import com.lambdaworks.redis.protocol.AsyncCommand;
import com.lambdaworks.redis.protocol.RedisCommand;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import net.bytebuddy.asm.Advice;

public class LettuceAsyncCommandsAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static AgentScope onEnter(@Advice.Argument(0) final RedisCommand<?, ?, ?> command) {
    return InstrumentationPoints.beforeCommand(command);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void onExit(
      @Advice.Argument(0) final RedisCommand<?, ?, ?> command,
      @Advice.Enter final AgentScope scope,
      @Advice.Thrown final Throwable throwable,
      @Advice.Return final AsyncCommand<?, ?, ?> asyncCommand) {
    InstrumentationPoints.afterCommand(command, scope, throwable, asyncCommand);
  }

}

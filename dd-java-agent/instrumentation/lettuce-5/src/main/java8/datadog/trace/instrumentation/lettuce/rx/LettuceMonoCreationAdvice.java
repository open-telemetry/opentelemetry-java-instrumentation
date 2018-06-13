package datadog.trace.instrumentation.lettuce.rx;

import datadog.trace.instrumentation.lettuce.LettuceInstrumentationUtil;
import io.lettuce.core.protocol.RedisCommand;
import java.util.function.Supplier;
import net.bytebuddy.asm.Advice;
import reactor.core.publisher.Mono;

public class LettuceMonoCreationAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static String extractCommandName(
      @Advice.Argument(0) final Supplier<RedisCommand> supplier) {
    return LettuceInstrumentationUtil.getCommandName(supplier.get());
  }

  // throwables wouldn't matter here, because no spans have been started due to redis command not being
  // run until the user subscribes to the Mono publisher
  @Advice.OnMethodExit(suppress = Throwable.class)
  public static void monitorSpan(
      @Advice.Enter final String commandName, @Advice.Return(readOnly = false) Mono<?> publisher) {

    boolean finishSpanOnClose = LettuceInstrumentationUtil.doFinishSpanEarly(commandName);
    LettuceMonoDualConsumer mdc = new LettuceMonoDualConsumer(commandName, finishSpanOnClose);
    publisher = publisher.doOnSubscribe(mdc);
    // register the call back to close the span only if necessary
    if (!finishSpanOnClose) {
      publisher = publisher.doOnSuccessOrError(mdc);
    }
  }
}

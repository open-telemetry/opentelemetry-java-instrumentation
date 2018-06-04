package datadog.trace.instrumentation.lettuce.rx;

import datadog.trace.instrumentation.lettuce.LettuceInstrumentationUtil;
import io.lettuce.core.protocol.RedisCommand;
import java.util.Map;
import java.util.function.Supplier;
import net.bytebuddy.asm.Advice;
import reactor.core.publisher.Mono;

public class LettuceMonoCreationAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static Map<String, String> extractCommand(
      @Advice.Argument(0) final Supplier<RedisCommand> supplier) {
    return LettuceInstrumentationUtil.getCommandInfo(supplier.get());
  }

  // throwables wouldn't matter here, because no spans have been started due to redis command not being
  // run until the user subscribes to the Mono publisher
  @Advice.OnMethodExit(suppress = Throwable.class)
  public static void monitorSpan(
      @Advice.Enter final Map<String, String> commandMap,
      @Advice.Return(readOnly = false) Mono<?> publisher) {

    boolean finishSpanOnClose = LettuceInstrumentationUtil.doFinishSpanEarly(commandMap);
    LettuceMonoDualConsumer mdc = new LettuceMonoDualConsumer(commandMap, finishSpanOnClose);
    publisher = publisher.doOnSubscribe(mdc);
    // register the call back to close the span only if necessary
    if (!finishSpanOnClose) {
      publisher = publisher.doOnSuccessOrError(mdc);
    }
  }
}

package datadog.trace.instrumentation.lettuce.rx;

import datadog.trace.instrumentation.lettuce.LettuceInstrumentationUtil;
import io.lettuce.core.protocol.RedisCommand;
import java.util.Map;
import java.util.function.Supplier;
import net.bytebuddy.asm.Advice;
import reactor.core.publisher.Flux;

public class FluxCreationAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static Map<String, String> extractCommand(
      @Advice.Argument(0) final Supplier<RedisCommand> supplier) {
    return LettuceInstrumentationUtil.getCommandInfo(supplier.get());
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void monitorSpan(
      @Advice.Enter final Map<String, String> commandMap,
      @Advice.Return(readOnly = false) Flux<?> publisher) {

    boolean finishSpanOnClose = LettuceInstrumentationUtil.doFinishSpanEarly(commandMap);
    FluxTerminationCancellableRunnable handler =
        new FluxTerminationCancellableRunnable(commandMap, finishSpanOnClose);
    publisher = publisher.doOnSubscribe(handler.getOnSubscribeConsumer());
    // don't register extra callbacks to finish the spans if the command being instrumented is one of those that return
    // Mono<Void> (In here a flux is created first and then converted to Mono<Void>)
    if (!finishSpanOnClose) {
      publisher = publisher.doOnEach(handler);
      publisher = publisher.doOnCancel(handler);
    }
  }
}

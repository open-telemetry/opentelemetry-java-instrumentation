package datadog.trace.instrumentation.lettuce.rx;

import static datadog.trace.instrumentation.lettuce.LettuceInstrumentationUtil.doFinishSpanEarly;

import io.lettuce.core.protocol.RedisCommand;
import java.util.function.Supplier;
import net.bytebuddy.asm.Advice;
import reactor.core.publisher.Flux;

public class LettuceFluxCreationAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static RedisCommand extractCommandName(
      @Advice.Argument(0) final Supplier<RedisCommand> supplier) {
    return supplier.get();
  }

  // if there is an exception thrown, then don't make spans
  @Advice.OnMethodExit(suppress = Throwable.class)
  public static void monitorSpan(
      @Advice.Enter final RedisCommand command,
      @Advice.Return(readOnly = false) Flux<?> publisher) {

    final boolean finishSpanOnClose = doFinishSpanEarly(command);
    final LettuceFluxTerminationRunnable handler =
        new LettuceFluxTerminationRunnable(command, finishSpanOnClose);
    publisher = publisher.doOnSubscribe(handler.getOnSubscribeConsumer());
    // don't register extra callbacks to finish the spans if the command being instrumented is one
    // of those that return
    // Mono<Void> (In here a flux is created first and then converted to Mono<Void>)
    if (!finishSpanOnClose) {
      publisher = publisher.doOnEach(handler);
      publisher = publisher.doOnCancel(handler);
    }
  }
}

package io.opentelemetry.auto.instrumentation.lettuce.rx;

import static io.opentelemetry.auto.instrumentation.api.AgentTracer.startSpan;
import static io.opentelemetry.auto.instrumentation.lettuce.LettuceClientDecorator.DECORATE;

import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.auto.instrumentation.api.AgentSpan;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public class LettuceMonoDualConsumer<R, T, U extends Throwable>
    implements Consumer<R>, BiConsumer<T, Throwable> {

  private AgentSpan span = null;
  private final RedisCommand command;
  private final boolean finishSpanOnClose;

  public LettuceMonoDualConsumer(final RedisCommand command, final boolean finishSpanOnClose) {
    this.command = command;
    this.finishSpanOnClose = finishSpanOnClose;
  }

  @Override
  public void accept(final R r) {
    span = startSpan("redis.query");
    DECORATE.afterStart(span);
    DECORATE.onCommand(span, command);
    if (finishSpanOnClose) {
      span.finish();
    }
  }

  @Override
  public void accept(final T t, final Throwable throwable) {
    if (span != null) {
      DECORATE.onError(span, throwable);
      DECORATE.beforeFinish(span);
      span.finish();
    } else {
      LoggerFactory.getLogger(Mono.class)
          .error(
              "Failed to finish this.span, BiConsumer cannot find this.span because "
                  + "it probably wasn't started.");
    }
  }
}

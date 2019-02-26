package datadog.trace.instrumentation.lettuce.rx;

import static datadog.trace.instrumentation.lettuce.LettuceClientDecorator.DECORATE;

import io.lettuce.core.protocol.RedisCommand;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public class LettuceMonoDualConsumer<R, T, U extends Throwable>
    implements Consumer<R>, BiConsumer<T, Throwable> {

  private Span span = null;
  private final RedisCommand command;
  private final boolean finishSpanOnClose;

  public LettuceMonoDualConsumer(final RedisCommand command, final boolean finishSpanOnClose) {
    this.command = command;
    this.finishSpanOnClose = finishSpanOnClose;
  }

  @Override
  public void accept(final R r) {
    final Scope scope = GlobalTracer.get().buildSpan("redis.query").startActive(finishSpanOnClose);
    span = scope.span();
    DECORATE.afterStart(span);
    DECORATE.onCommand(span, command);
    scope.close();
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

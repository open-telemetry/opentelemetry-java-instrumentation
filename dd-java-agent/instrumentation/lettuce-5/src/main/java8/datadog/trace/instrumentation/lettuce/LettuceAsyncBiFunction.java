package datadog.trace.instrumentation.lettuce;

import static datadog.trace.instrumentation.lettuce.LettuceClientDecorator.DECORATE;

import io.opentracing.Span;
import java.util.concurrent.CancellationException;
import java.util.function.BiFunction;

/**
 * Callback class to close the span on an error or a success in the RedisFuture returned by the
 * lettuce async API
 *
 * @param <T> the normal completion result
 * @param <U> the error
 * @param <R> the return type, should be null since nothing else should happen from tracing
 *     standpoint after the span is closed
 */
public class LettuceAsyncBiFunction<T extends Object, U extends Throwable, R extends Object>
    implements BiFunction<T, Throwable, R> {

  private final Span span;

  public LettuceAsyncBiFunction(final Span span) {
    this.span = span;
  }

  @Override
  public R apply(final T t, final Throwable throwable) {
    if (throwable instanceof CancellationException) {
      span.setTag("db.command.cancelled", true);
    } else {
      DECORATE.onError(span, throwable);
    }
    DECORATE.beforeFinish(span);
    span.finish();
    return null;
  }
}

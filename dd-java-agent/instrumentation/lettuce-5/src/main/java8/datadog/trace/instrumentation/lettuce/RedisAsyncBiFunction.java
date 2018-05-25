package datadog.trace.instrumentation.lettuce;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import java.util.Collections;
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
public class RedisAsyncBiFunction<T extends Object, U extends Throwable, R extends Object>
    implements BiFunction<T, Throwable, R> {

  private final Span span;

  public RedisAsyncBiFunction(Span span) {
    this.span = span;
  }

  @Override
  public R apply(T t, Throwable throwable) {
    if (throwable != null) {
      Tags.ERROR.set(this.span, true);
      this.span.log(Collections.singletonMap("error.object", throwable));
    }
    this.span.finish();
    return null;
  }
}

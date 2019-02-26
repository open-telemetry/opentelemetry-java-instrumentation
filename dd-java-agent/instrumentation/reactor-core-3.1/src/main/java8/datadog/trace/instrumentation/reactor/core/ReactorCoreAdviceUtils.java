package datadog.trace.instrumentation.reactor.core;

import static io.opentracing.log.Fields.ERROR_OBJECT;
import static reactor.core.publisher.Operators.lift;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import java.util.Collections;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Slf4j
public class ReactorCoreAdviceUtils {

  public static final String PUBLISHER_CONTEXT_KEY =
      "datadog.trace.instrumentation.reactor.core.Span";

  public static <T> Mono<T> setPublisherSpan(final Mono<T> mono, final Span span) {
    return mono.<T>transform(finishSpanNextOrError())
        .subscriberContext(Context.of(PUBLISHER_CONTEXT_KEY, span));
  }

  public static <T> Flux<T> setPublisherSpan(final Flux<T> flux, final Span span) {
    return flux.<T>transform(finishSpanNextOrError())
        .subscriberContext(Context.of(PUBLISHER_CONTEXT_KEY, span));
  }

  /**
   * Idea for this has been lifted from https://github.com/reactor/reactor-core/issues/947. Newer
   * versions of reactor-core have easier way to access context but we want to support older
   * versions.
   */
  public static <T, IP>
      Function<? super Publisher<T>, ? extends Publisher<T>> finishSpanNextOrError() {
    return lift((scannable, subscriber) -> new TracingSubscriber<>(subscriber));
  }

  public static void finishSpanIfPresent(final Context context, final Throwable throwable) {
    finishSpanIfPresent(context.getOrDefault(PUBLISHER_CONTEXT_KEY, (Span) null), throwable);
  }

  public static void finishSpanIfPresent(final Span span, final Throwable throwable) {
    if (span != null) {
      if (throwable != null) {
        Tags.ERROR.set(span, true);
        span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
      }
      span.finish();
    }
  }

  public static class TracingSubscriber<T> implements CoreSubscriber<T> {

    private final Context context;
    private final CoreSubscriber<? super T> subscriber;

    public TracingSubscriber(final CoreSubscriber<? super T> subscriber) {
      this.subscriber = subscriber;
      context = subscriber.currentContext();
    }

    @Override
    public void onNext(final T event) {
      subscriber.onNext(event);
    }

    @Override
    public void onError(final Throwable throwable) {
      finishSpanIfPresent(context, throwable);
      subscriber.onError(throwable);
    }

    @Override
    public void onComplete() {
      finishSpanIfPresent(context, null);
      subscriber.onComplete();
    }

    @Override
    public Context currentContext() {
      return context;
    }

    @Override
    public void onSubscribe(final Subscription s) {
      subscriber.onSubscribe(s);
    }
  }
}

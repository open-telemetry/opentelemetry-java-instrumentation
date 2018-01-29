package datadog.trace.instrumentation.kafka_clients;

import io.opentracing.Scope;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import java.util.Iterator;
import lombok.extern.slf4j.Slf4j;

public class TracingIterable<T> implements Iterable<T> {
  private final Iterable<T> delegateIterable;
  private final String operationName;
  private final SpanBuilderDecorator<T> decorator;

  public TracingIterable(
      final Iterable<T> delegateIterable,
      final String operationName,
      final SpanBuilderDecorator<T> decorator) {
    this.delegateIterable = delegateIterable;
    this.operationName = operationName;
    this.decorator = decorator;
  }

  @Override
  public Iterator<T> iterator() {
    return new TracingIterator<>(delegateIterable.iterator(), operationName, decorator);
  }

  @Slf4j
  public static class TracingIterator<T> implements Iterator<T> {
    private final Iterator<T> delegateIterator;
    private final String operationName;
    private final SpanBuilderDecorator<T> decorator;

    private Scope currentScope;

    public TracingIterator(
        final Iterator<T> delegateIterator,
        final String operationName,
        final SpanBuilderDecorator<T> decorator) {
      this.delegateIterator = delegateIterator;
      this.operationName = operationName;
      this.decorator = decorator;
    }

    @Override
    public boolean hasNext() {
      if (currentScope != null) {
        currentScope.close();
        currentScope = null;
      }
      return delegateIterator.hasNext();
    }

    @Override
    public T next() {
      if (currentScope != null) {
        // in case they didn't call hasNext()...
        currentScope.close();
        currentScope = null;
      }

      final T next = delegateIterator.next();

      try {
        if (next != null) {
          final Tracer.SpanBuilder spanBuilder = GlobalTracer.get().buildSpan(operationName);
          decorator.decorate(spanBuilder, next);
          currentScope = spanBuilder.startActive(true);
        }
      } finally {
        return next;
      }
    }

    @Override
    public void remove() {
      delegateIterator.remove();
    }
  }

  public interface SpanBuilderDecorator<T> {
    void decorate(Tracer.SpanBuilder spanBuilder, T context);
  }
}

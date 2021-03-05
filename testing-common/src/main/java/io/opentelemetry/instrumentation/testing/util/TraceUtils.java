package io.opentelemetry.instrumentation.testing.util;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;

/** Utility class for creating spans in tests. */
public final class TraceUtils {

  private static final TestTracer TRACER = new TestTracer();

  public static <E extends Exception> void withSpan(String spanName, ThrowingRunnable<E> callback)
      throws E {
    withSpan(
        spanName,
        () -> {
          callback.run();
          return null;
        });
  }

  public static <T, E extends Throwable> T withSpan(
      String spanName, ThrowingSupplier<T, E> callback) throws E {
    Context context = TRACER.startSpan(spanName);
    try (Scope ignored = context.makeCurrent()) {
      T result = callback.get();
      TRACER.end(context);
      return result;
    } catch (Throwable t) {
      TRACER.endExceptionally(context, t);
      throw t;
    }
  }

  public static <E extends Throwable> void withClientSpan(
      String spanName, ThrowingRunnable<E> callback) throws E {
    withClientSpan(
        spanName,
        () -> {
          callback.run();
          return null;
        });
  }

  public static <T, E extends Throwable> T withClientSpan(
      String spanName, ThrowingSupplier<T, E> callback) throws E {
    Context context = TRACER.startClientSpan(spanName);
    try (Scope ignored = context.makeCurrent()) {
      T result = callback.get();
      TRACER.end(context);
      return result;
    } catch (Throwable t) {
      TRACER.endExceptionally(context, t);
      throw t;
    }
  }

  public static <E extends Throwable> void withServerSpan(
      String spanName, ThrowingRunnable<E> callback) throws E {
    withServerSpan(
        spanName,
        () -> {
          callback.run();
          return null;
        });
  }

  public static <T, E extends Throwable> T withServerSpan(
      String spanName, ThrowingSupplier<T, E> callback) throws E {
    Context context = TRACER.startServerSpan(spanName);
    try (Scope ignored = context.makeCurrent()) {
      T result = callback.get();
      TRACER.end(context);
      return result;
    } catch (Throwable t) {
      TRACER.endExceptionally(context, t);
      throw t;
    }
  }

  private static final class TestTracer extends BaseTracer {
    @Override
    protected String getInstrumentationName() {
      return "test";
    }

    Context startClientSpan(String name) {
      Span span = spanBuilder(name, SpanKind.CLIENT).startSpan();
      return withClientSpan(Context.current(), span);
    }

    Context startServerSpan(String name) {
      Span span = spanBuilder(name, SpanKind.SERVER).startSpan();
      return withServerSpan(Context.current(), span);
    }
  }

  private TraceUtils() {}
}

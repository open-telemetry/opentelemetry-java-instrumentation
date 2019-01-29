package datadog.trace.tracer.ext;

import datadog.trace.tracer.Continuation;
import datadog.trace.tracer.Span;
import datadog.trace.tracer.Tracer;

// Keeping in PR for potential discussions. Will eventually remove.
// TODO: remove
class Examples {
  private Examples() {}

  public static void test() {
    final Throwable someThrowable = null;
    // registration
    TracerContext.registerGlobalContext(new TracerContext(Tracer.builder().build()), false);

    // scope
    final TracerContext ctx = TracerContext.getGlobalContext();
    // without try-with-resources
    {
      final Span rootSpan = ctx.getTracer().buildTrace(null);
      final Scope scope = ctx.pushScope(rootSpan);
      rootSpan.attachThrowable(someThrowable);
      scope.close();
      rootSpan.finish();
    }

    /*
    // with try-with-resources finishOnClose=true
    {
      Span rootSpan = ctx.getTracer().buildTrace(null);
      try (Scope scope = ctx.pushScope(rootSpan)) {
        try {
          // the body
        } catch (Throwable t) {
          rootSpan.setError(true);
          rootSpan.attachThrowable(t);
          throw t;
        }
      }
    }
    */

    // with try-with-resources finishOnClose=false
    {
      final Span rootSpan = ctx.getTracer().buildTrace(null);
      try (final Scope scope = ctx.pushScope(rootSpan)) {
        // the body
      } catch (final Throwable t) {
        rootSpan.attachThrowable(t);
        throw t;
      } finally {
        rootSpan.finish();
      }
    }

    // continuations
    {
      final Span rootSpan = ctx.getTracer().buildTrace(null);
      final Continuation cont = rootSpan.getTrace().createContinuation(rootSpan);
      { // on another thread
        final Span parent = cont.getSpan();
        try {
          // body
        } finally {
          cont.close();
        }
      }
    }

    // create a span as a child of the currently active span
    final Span childSpan =
        ctx.peekScope().span().getTrace().createSpan(ctx.peekScope().span().getContext());
  }
}

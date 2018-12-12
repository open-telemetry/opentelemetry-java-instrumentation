package datadog.trace.tracer.ext;

import datadog.trace.tracer.Continuation;
import datadog.trace.tracer.Span;
import datadog.trace.tracer.Tracer;

/**
 * Provides a place to store a shared global tracer with convenient span-building helper methods.
 *
 * <p>Also maintains a stack of active scopes (or scope-stack). The span of the scope on the top of
 * the scope stack is used as the parent for newly created spans.
 *
 * <p>This class is thread safe.
 */
public final class TracerContext {
  // global TracerContext
  /** Get the global TracerContext */
  public static TracerContext getGlobalContext() {
    return null;
  }

  /**
   * Register the global TracerContext.
   *
   * @param context The context to register.
   * @param replaceExisting If true, the existing global TracerContext will be replaced
   * @return The old global TracerContext, or null if no previous context ws registered
   */
  public static TracerContext registerGlobalContext(
      final TracerContext context, final boolean replaceExisting) {
    return null;
  }

  /** @return True if a global TracerContext has been registered */
  public static boolean isRegistered() {
    return false;
  }

  private final Tracer tracer;

  public TracerContext(final Tracer tracer) {
    this.tracer = tracer;
  }

  /** @return The tracer associated with this TracerContext */
  public Tracer getTracer() {
    return tracer;
  }

  // TODO: convenience APIs like buildSpan, etc.

  /**
   * Push a new scope to the top of this scope-stack. The scope's span will be the given span.
   *
   * @param span
   * @return
   */
  public Scope pushScope(final Span span) {
    return null;
  }

  /**
   * Push a new scope to the top of this scope-stack. The scope's span will be the continuation's
   * span.
   *
   * @param continuation
   * @return
   */
  public Scope pushScope(final Continuation continuation) {
    return null;
  }

  /**
   * Pop the given scope off the top of the scope stack.
   *
   * <p>If the given scope is not the topmost scope on the stack an error will be thrown.
   *
   * @param scope the topmost scope in the scope stack.
   */
  public void popScope(final Scope scope) {}

  /** @return The scope on the top of this scope-stack or null if there is no active scope. */
  public Scope peekScope() {
    return null;
  }
}

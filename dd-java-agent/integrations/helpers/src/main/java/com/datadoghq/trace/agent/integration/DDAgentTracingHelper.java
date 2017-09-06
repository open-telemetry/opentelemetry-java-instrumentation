package com.datadoghq.trace.agent.integration;

import io.opentracing.NoopTracerFactory;
import io.opentracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.jboss.byteman.rule.Rule;

/**
 * This class provides helpful stuff in order to easy patch object using Byteman rules
 *
 * @param <T> The type of the object to patch
 */
@Slf4j
public abstract class DDAgentTracingHelper<T> extends OpenTracingHelper {

  /**
   * The current instance of the tracer. If something goes wrong during the resolution, we provides
   * a NoopTracer.
   */
  protected final Tracer tracer;

  DDAgentTracingHelper(final Rule rule) {
    super(rule);
    Tracer tracerResolved;
    try {
      tracerResolved = getTracer();
      tracerResolved = tracerResolved == null ? NoopTracerFactory.create() : tracerResolved;
    } catch (final Exception e) {
      tracerResolved = NoopTracerFactory.create();
      log.warn("Failed to retrieve the tracer, using a NoopTracer instead: {}", e.getMessage());
      log.warn(e.getMessage(), e);
    }
    tracer = tracerResolved;
  }

  /**
   * This method takes an object and applies some mutation in order to add tracing capabilities.
   * This method should never return any Exception in order to not stop the app traced.
   *
   * <p>This method should be defined as final, but something Byteman need to define this one with
   * the explicit type (i.e. without using generic), so this is why we don't use final here.
   *
   * @param args The object to patch, the type is defined by the subclass instantiation
   * @return The object patched
   */
  public T patch(final T args) {

    if (args == null) {
      log.debug("Skipping rule={} because the input arg is null", rule.getName());
      return null;
    }

    final String className = args.getClass().getName();
    log.debug("Try to patch class={}", className);

    T patched;
    try {
      patched = doPatch(args);
      log.debug("class={} patched", className);
    } catch (final Throwable e) {
      log.warn("Failed to patch class={}, reason: {}", className, e.getMessage());
      log.warn(e.getMessage(), e);
      patched = args;
    }
    return patched;
  }

  /**
   * The current implementation of the patch
   *
   * @param obj the object to patch
   * @return the object patched
   * @throws Exception The exceptions are managed directly to the patch method
   */
  protected abstract T doPatch(T obj) throws Exception;
}

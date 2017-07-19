package com.datadoghq.trace.agent.integration;

import io.opentracing.NoopTracerFactory;
import io.opentracing.Tracer;
import io.opentracing.contrib.agent.OpenTracingHelper;
import org.jboss.byteman.rule.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides helpful stuff in order to easy patch object using Byteman rules
 *
 * @param <T> The type of the object to patch
 */
public abstract class DDAgentTracingHelper<T> extends OpenTracingHelper {

  private static final Logger LOGGER = LoggerFactory.getLogger(DDAgentTracingHelper.class);

  /**
   * The current instance of the tracer. If something goes wrong during the resolution, we provides
   * a NoopTracer.
   */
  protected final Tracer tracer;

  DDAgentTracingHelper(Rule rule) {
    super(rule);
    Tracer tracerResolved;
    try {
      tracerResolved = getTracer();
      tracerResolved = tracerResolved == null ? NoopTracerFactory.create() : tracerResolved;
    } catch (Exception e) {
      tracerResolved = NoopTracerFactory.create();
      LOGGER.warn("Failed to retrieve the tracer, using a NoopTracer instead: {}", e.getMessage());
      LOGGER.warn(e.getMessage(), e);
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
  public T patch(T args) {

    if (args == null) {
      LOGGER.debug("Skipping rule={} because the input arg is null", rule.getName());
      return null;
    }

    String className = args.getClass().getName();
    LOGGER.debug("Try to patch class={}", className);

    T patched;
    try {
      patched = doPatch(args);
      LOGGER.debug("class={} patched", className);
    } catch (Throwable e) {
      LOGGER.warn("Failed to patch class={}, reason: {}", className, e.getMessage());
      LOGGER.warn(e.getMessage(), e);
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

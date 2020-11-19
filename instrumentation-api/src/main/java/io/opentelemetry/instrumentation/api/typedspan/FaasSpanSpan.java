/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.typedspan;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

public class FaasSpanSpan extends DelegatingSpan implements FaasSpanSemanticConvention {

  protected FaasSpanSpan(Span span) {
    super(span);
  }

  /**
   * Entry point to generate a {@link FaasSpanSpan}.
   *
   * @param tracer Tracer to use
   * @param spanName Name for the {@link Span}
   * @return a {@link FaasSpanSpan} object.
   */
  public static FaasSpanSpanBuilder createFaasSpanSpan(Tracer tracer, String spanName) {
    return new FaasSpanSpanBuilder(tracer, spanName);
  }

  /** @return the Span used internally */
  @Override
  public Span getSpan() {
    return this.delegate;
  }

  /** Terminates the Span. Here there is the checking for required attributes. */
  @Override
  public void end() {
    delegate.end();
  }

  /**
   * Sets faas.trigger.
   *
   * @param faasTrigger Type of the trigger on which the function is executed.
   */
  @Override
  public FaasSpanSemanticConvention setFaasTrigger(String faasTrigger) {
    delegate.setAttribute("faas.trigger", faasTrigger);
    return this;
  }

  /**
   * Sets faas.execution.
   *
   * @param faasExecution The execution id of the current function execution.
   */
  @Override
  public FaasSpanSemanticConvention setFaasExecution(String faasExecution) {
    delegate.setAttribute("faas.execution", faasExecution);
    return this;
  }

  /** Builder class for {@link FaasSpanSpan}. */
  public static class FaasSpanSpanBuilder {
    // Protected because maybe we want to extend manually these classes
    protected SpanBuilder internalBuilder;

    protected FaasSpanSpanBuilder(Tracer tracer, String spanName) {
      internalBuilder = tracer.spanBuilder(spanName);
    }

    public FaasSpanSpanBuilder(SpanBuilder spanBuilder) {
      this.internalBuilder = spanBuilder;
    }

    public SpanBuilder getSpanBuilder() {
      return this.internalBuilder;
    }

    /** sets the {@link Span} parent. */
    public FaasSpanSpanBuilder setParent(Context context) {
      this.internalBuilder.setParent(context);
      return this;
    }

    /** this method sets the type of the {@link Span} is only available in the builder. */
    public FaasSpanSpanBuilder setKind(Span.Kind kind) {
      internalBuilder.setSpanKind(kind);
      return this;
    }

    /** starts the span */
    public FaasSpanSpan start() {
      // check for sampling relevant field here, but there are none.
      return new FaasSpanSpan(this.internalBuilder.startSpan());
    }

    /**
     * Sets faas.trigger.
     *
     * @param faasTrigger Type of the trigger on which the function is executed.
     */
    public FaasSpanSpanBuilder setFaasTrigger(String faasTrigger) {
      internalBuilder.setAttribute("faas.trigger", faasTrigger);
      return this;
    }

    /**
     * Sets faas.execution.
     *
     * @param faasExecution The execution id of the current function execution.
     */
    public FaasSpanSpanBuilder setFaasExecution(String faasExecution) {
      internalBuilder.setAttribute("faas.execution", faasExecution);
      return this;
    }
  }
}

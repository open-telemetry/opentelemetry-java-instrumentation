/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.typedspan;

import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.Tracer;
import java.util.logging.Logger;

/**
 * <b>Required attributes:</b>
 *
 * <ul>
 *   <li>faas.trigger: Type of the trigger on which the function is executed.
 * </ul>
 *
 * <b>Conditional attributes:</b>
 *
 * <ul>
 * </ul>
 */
public class FaasSpan extends DelegatingSpan implements FaasSemanticConvention {

  enum AttributeStatus {
    EMPTY,
    FAAS_TRIGGER,
    FAAS_EXECUTION;

    @SuppressWarnings("ImmutableEnumChecker")
    private long flag;

    AttributeStatus() {
      this.flag = 1L << this.ordinal();
    }

    public boolean isSet(AttributeStatus attribute) {
      return (this.flag & attribute.flag) > 0;
    }

    public void set(AttributeStatus attribute) {
      this.flag |= attribute.flag;
    }

    public void set(long attribute) {
      this.flag = attribute;
    }

    public long getValue() {
      return flag;
    }
  }

  @SuppressWarnings("unused")
  private static final Logger logger = Logger.getLogger(FaasSpan.class.getName());

  public final AttributeStatus status;

  protected FaasSpan(Span span, AttributeStatus status) {
    super(span);
    this.status = status;
  }

  /**
   * Entry point to generate a {@link FaasSpan}.
   *
   * @param tracer Tracer to use
   * @param spanName Name for the {@link Span}
   * @return a {@link FaasSpan} object.
   */
  public static FaasSpanBuilder createFaasSpan(Tracer tracer, String spanName) {
    return new FaasSpanBuilder(tracer, spanName);
  }

  /** @return the Span used internally */
  @Override
  public Span getSpan() {
    return this.delegate;
  }

  /** Terminates the Span. Here there is the checking for required attributes. */
  @Override
  @SuppressWarnings("UnnecessaryParentheses")
  public void end() {
    delegate.end();

    // required attributes
    if (!this.status.isSet(AttributeStatus.FAAS_TRIGGER)) {
      logger.warning("Wrong usage - Span missing faas.trigger attribute");
    }
    // extra constraints.
    // conditional attributes
  }

  /**
   * Sets faas.trigger.
   *
   * @param faasTrigger Type of the trigger on which the function is executed..
   */
  @Override
  public FaasSemanticConvention setFaasTrigger(String faasTrigger) {
    status.set(AttributeStatus.FAAS_TRIGGER);
    delegate.setAttribute("faas.trigger", faasTrigger);
    return this;
  }

  /**
   * Sets faas.execution.
   *
   * @param faasExecution The execution id of the current function execution..
   */
  @Override
  public FaasSemanticConvention setFaasExecution(String faasExecution) {
    status.set(AttributeStatus.FAAS_EXECUTION);
    delegate.setAttribute("faas.execution", faasExecution);
    return this;
  }

  /** Builder class for {@link FaasSpan}. */
  public static class FaasSpanBuilder {
    // Protected because maybe we want to extend manually these classes
    protected Builder internalBuilder;
    protected AttributeStatus status = AttributeStatus.EMPTY;

    protected FaasSpanBuilder(Tracer tracer, String spanName) {
      internalBuilder = tracer.spanBuilder(spanName);
    }

    public FaasSpanBuilder(Builder spanBuilder, long attributes) {
      this.internalBuilder = spanBuilder;
      this.status.set(attributes);
    }

    public Builder getSpanBuilder() {
      return this.internalBuilder;
    }

    /** sets the {@link Span} parent. */
    public FaasSpanBuilder setParent(Span parent) {
      this.internalBuilder.setParent(parent);
      return this;
    }

    /** sets the {@link Span} parent. */
    public FaasSpanBuilder setParent(SpanContext remoteParent) {
      this.internalBuilder.setParent(remoteParent);
      return this;
    }

    /** this method sets the type of the {@link Span} is only available in the builder. */
    public FaasSpanBuilder setKind(Kind kind) {
      internalBuilder.setSpanKind(kind);
      return this;
    }

    /** starts the span */
    public FaasSpan start() {
      // check for sampling relevant field here, but there are none.
      return new FaasSpan(this.internalBuilder.startSpan(), status);
    }

    /**
     * Sets faas.trigger.
     *
     * @param faasTrigger Type of the trigger on which the function is executed..
     */
    public FaasSpanBuilder setFaasTrigger(String faasTrigger) {
      status.set(AttributeStatus.FAAS_TRIGGER);
      internalBuilder.setAttribute("faas.trigger", faasTrigger);
      return this;
    }

    /**
     * Sets faas.execution.
     *
     * @param faasExecution The execution id of the current function execution..
     */
    public FaasSpanBuilder setFaasExecution(String faasExecution) {
      status.set(AttributeStatus.FAAS_EXECUTION);
      internalBuilder.setAttribute("faas.execution", faasExecution);
      return this;
    }
  }
}

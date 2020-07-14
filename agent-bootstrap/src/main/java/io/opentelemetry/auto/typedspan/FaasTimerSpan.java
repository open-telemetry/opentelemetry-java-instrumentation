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
 *   <li>faas.time: A string containing the function invocation time in the [ISO
 *       8601](https://www.iso.org/iso-8601-date-and-time-format.html) format expressed in
 *       [UTC](https://www.w3.org/TR/NOTE-datetime).
 * </ul>
 *
 * <b>Conditional attributes:</b>
 *
 * <ul>
 * </ul>
 */
public class FaasTimerSpan extends DelegatingSpan implements FaasTimerSemanticConvention {

  enum AttributeStatus {
    EMPTY,
    FAAS_TRIGGER,
    FAAS_EXECUTION,
    FAAS_TIME,
    FAAS_CRON;

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
  private static final Logger logger = Logger.getLogger(FaasTimerSpan.class.getName());

  public final AttributeStatus status;

  protected FaasTimerSpan(Span span, AttributeStatus status) {
    super(span);
    this.status = status;
  }

  /**
   * Entry point to generate a {@link FaasTimerSpan}.
   *
   * @param tracer Tracer to use
   * @param spanName Name for the {@link Span}
   * @return a {@link FaasTimerSpan} object.
   */
  public static FaasTimerSpanBuilder createFaasTimerSpan(Tracer tracer, String spanName) {
    return new FaasTimerSpanBuilder(tracer, spanName);
  }

  /**
   * Creates a {@link FaasTimerSpan} from a {@link FaasSpan}.
   *
   * @param builder {@link FaasSpan.FaasSpanBuilder} to use.
   * @return a {@link FaasTimerSpan} object built from a {@link FaasSpan}.
   */
  public static FaasTimerSpanBuilder createFaasTimerSpan(FaasSpan.FaasSpanBuilder builder) {
    // we accept a builder from Faas since FaasTimer "extends" Faas
    return new FaasTimerSpanBuilder(builder.getSpanBuilder(), builder.status.getValue());
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
    if (!this.status.isSet(AttributeStatus.FAAS_TIME)) {
      logger.warning("Wrong usage - Span missing faas.time attribute");
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
  public FaasTimerSemanticConvention setFaasTrigger(String faasTrigger) {
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
  public FaasTimerSemanticConvention setFaasExecution(String faasExecution) {
    status.set(AttributeStatus.FAAS_EXECUTION);
    delegate.setAttribute("faas.execution", faasExecution);
    return this;
  }

  /**
   * Sets faas.time.
   *
   * @param faasTime A string containing the function invocation time in the [ISO
   *     8601](https://www.iso.org/iso-8601-date-and-time-format.html) format expressed in
   *     [UTC](https://www.w3.org/TR/NOTE-datetime)..
   */
  @Override
  public FaasTimerSemanticConvention setFaasTime(String faasTime) {
    status.set(AttributeStatus.FAAS_TIME);
    delegate.setAttribute("faas.time", faasTime);
    return this;
  }

  /**
   * Sets faas.cron.
   *
   * @param faasCron A string containing the schedule period as [Cron
   *     Expression](https://docs.oracle.com/cd/E12058_01/doc/doc.1014/e12030/cron_expressions.htm)..
   */
  @Override
  public FaasTimerSemanticConvention setFaasCron(String faasCron) {
    status.set(AttributeStatus.FAAS_CRON);
    delegate.setAttribute("faas.cron", faasCron);
    return this;
  }

  /** Builder class for {@link FaasTimerSpan}. */
  public static class FaasTimerSpanBuilder {
    // Protected because maybe we want to extend manually these classes
    protected Builder internalBuilder;
    protected AttributeStatus status = AttributeStatus.EMPTY;

    protected FaasTimerSpanBuilder(Tracer tracer, String spanName) {
      internalBuilder = tracer.spanBuilder(spanName);
    }

    public FaasTimerSpanBuilder(Builder spanBuilder, long attributes) {
      this.internalBuilder = spanBuilder;
      this.status.set(attributes);
    }

    public Builder getSpanBuilder() {
      return this.internalBuilder;
    }

    /** sets the {@link Span} parent. */
    public FaasTimerSpanBuilder setParent(Span parent) {
      this.internalBuilder.setParent(parent);
      return this;
    }

    /** sets the {@link Span} parent. */
    public FaasTimerSpanBuilder setParent(SpanContext remoteParent) {
      this.internalBuilder.setParent(remoteParent);
      return this;
    }

    /** this method sets the type of the {@link Span} is only available in the builder. */
    public FaasTimerSpanBuilder setKind(Kind kind) {
      internalBuilder.setSpanKind(kind);
      return this;
    }

    /** starts the span */
    public FaasTimerSpan start() {
      // check for sampling relevant field here, but there are none.
      return new FaasTimerSpan(this.internalBuilder.startSpan(), status);
    }

    /**
     * Sets faas.trigger.
     *
     * @param faasTrigger Type of the trigger on which the function is executed..
     */
    public FaasTimerSpanBuilder setFaasTrigger(String faasTrigger) {
      status.set(AttributeStatus.FAAS_TRIGGER);
      internalBuilder.setAttribute("faas.trigger", faasTrigger);
      return this;
    }

    /**
     * Sets faas.execution.
     *
     * @param faasExecution The execution id of the current function execution..
     */
    public FaasTimerSpanBuilder setFaasExecution(String faasExecution) {
      status.set(AttributeStatus.FAAS_EXECUTION);
      internalBuilder.setAttribute("faas.execution", faasExecution);
      return this;
    }

    /**
     * Sets faas.time.
     *
     * @param faasTime A string containing the function invocation time in the [ISO
     *     8601](https://www.iso.org/iso-8601-date-and-time-format.html) format expressed in
     *     [UTC](https://www.w3.org/TR/NOTE-datetime)..
     */
    public FaasTimerSpanBuilder setFaasTime(String faasTime) {
      status.set(AttributeStatus.FAAS_TIME);
      internalBuilder.setAttribute("faas.time", faasTime);
      return this;
    }

    /**
     * Sets faas.cron.
     *
     * @param faasCron A string containing the schedule period as [Cron
     *     Expression](https://docs.oracle.com/cd/E12058_01/doc/doc.1014/e12030/cron_expressions.htm)..
     */
    public FaasTimerSpanBuilder setFaasCron(String faasCron) {
      status.set(AttributeStatus.FAAS_CRON);
      internalBuilder.setAttribute("faas.cron", faasCron);
      return this;
    }
  }
}

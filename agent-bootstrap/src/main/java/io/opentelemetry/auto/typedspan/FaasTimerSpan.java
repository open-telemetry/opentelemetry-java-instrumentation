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

public class FaasTimerSpan extends DelegatingSpan implements FaasTimerSemanticConvention {

  protected FaasTimerSpan(Span span) {
    super(span);
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
   * Creates a {@link FaasTimerSpan} from a {@link FaasSpanSpan}.
   *
   * @param builder {@link FaasSpanSpan.FaasSpanSpanBuilder} to use.
   * @return a {@link FaasTimerSpan} object built from a {@link FaasSpanSpan}.
   */
  public static FaasTimerSpanBuilder createFaasTimerSpan(FaasSpanSpan.FaasSpanSpanBuilder builder) {
    // we accept a builder from FaasSpan since FaasTimer "extends" FaasSpan
    return new FaasTimerSpanBuilder(builder.getSpanBuilder());
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
  public FaasTimerSemanticConvention setFaasTrigger(String faasTrigger) {
    delegate.setAttribute("faas.trigger", faasTrigger);
    return this;
  }

  /**
   * Sets faas.execution.
   *
   * @param faasExecution The execution id of the current function execution.
   */
  @Override
  public FaasTimerSemanticConvention setFaasExecution(String faasExecution) {
    delegate.setAttribute("faas.execution", faasExecution);
    return this;
  }

  /**
   * Sets faas.time.
   *
   * @param faasTime A string containing the function invocation time in the [ISO
   *     8601](https://www.iso.org/iso-8601-date-and-time-format.html) format expressed in
   *     [UTC](https://www.w3.org/TR/NOTE-datetime).
   */
  @Override
  public FaasTimerSemanticConvention setFaasTime(String faasTime) {
    delegate.setAttribute("faas.time", faasTime);
    return this;
  }

  /**
   * Sets faas.cron.
   *
   * @param faasCron A string containing the schedule period as [Cron
   *     Expression](https://docs.oracle.com/cd/E12058_01/doc/doc.1014/e12030/cron_expressions.htm).
   */
  @Override
  public FaasTimerSemanticConvention setFaasCron(String faasCron) {
    delegate.setAttribute("faas.cron", faasCron);
    return this;
  }

  /** Builder class for {@link FaasTimerSpan}. */
  public static class FaasTimerSpanBuilder {
    // Protected because maybe we want to extend manually these classes
    protected Span.Builder internalBuilder;

    protected FaasTimerSpanBuilder(Tracer tracer, String spanName) {
      internalBuilder = tracer.spanBuilder(spanName);
    }

    public FaasTimerSpanBuilder(Span.Builder spanBuilder) {
      this.internalBuilder = spanBuilder;
    }

    public Span.Builder getSpanBuilder() {
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
    public FaasTimerSpanBuilder setKind(Span.Kind kind) {
      internalBuilder.setSpanKind(kind);
      return this;
    }

    /** starts the span */
    public FaasTimerSpan start() {
      // check for sampling relevant field here, but there are none.
      return new FaasTimerSpan(this.internalBuilder.startSpan());
    }

    /**
     * Sets faas.trigger.
     *
     * @param faasTrigger Type of the trigger on which the function is executed.
     */
    public FaasTimerSpanBuilder setFaasTrigger(String faasTrigger) {
      internalBuilder.setAttribute("faas.trigger", faasTrigger);
      return this;
    }

    /**
     * Sets faas.execution.
     *
     * @param faasExecution The execution id of the current function execution.
     */
    public FaasTimerSpanBuilder setFaasExecution(String faasExecution) {
      internalBuilder.setAttribute("faas.execution", faasExecution);
      return this;
    }

    /**
     * Sets faas.time.
     *
     * @param faasTime A string containing the function invocation time in the [ISO
     *     8601](https://www.iso.org/iso-8601-date-and-time-format.html) format expressed in
     *     [UTC](https://www.w3.org/TR/NOTE-datetime).
     */
    public FaasTimerSpanBuilder setFaasTime(String faasTime) {
      internalBuilder.setAttribute("faas.time", faasTime);
      return this;
    }

    /**
     * Sets faas.cron.
     *
     * @param faasCron A string containing the schedule period as [Cron
     *     Expression](https://docs.oracle.com/cd/E12058_01/doc/doc.1014/e12030/cron_expressions.htm).
     */
    public FaasTimerSpanBuilder setFaasCron(String faasCron) {
      internalBuilder.setAttribute("faas.cron", faasCron);
      return this;
    }
  }
}

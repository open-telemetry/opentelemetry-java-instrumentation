/*
 * Copyright 2020, OpenTelemetry Authors
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

package io.opentelemetry.helpers.core;

import io.opentelemetry.context.Scope;
import io.opentelemetry.distributedcontext.DistributedContext;
import io.opentelemetry.distributedcontext.DistributedContextManager;
import io.opentelemetry.metrics.MeasureDouble;
import io.opentelemetry.metrics.MeasureLong;
import io.opentelemetry.metrics.Meter;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.Tracer;
import java.util.concurrent.Callable;

/**
 * Abstract base implementation of {@link SpanDecorator}.
 *
 * @param <C> the context propagation carrier type
 * @param <Q> the request or input object type
 * @param <P> the response or output object type
 */
public abstract class BaseSpanDecorator<C, Q, P> implements SpanDecorator<C, Q, P> {

  private static final MessageMetadataExtractor DEFAULT_MESSAGE_METADATA_EXTRACTOR =
      new DefaultMessageMetadataExtractor();

  private final Tracer tracer;
  private final DistributedContextManager contextManager;
  private final Meter meter;
  private final StatusTranslator<P> defaultStatusTranslator = new DefaultStatusTranslator<>();

  /**
   * Constructs a decorator object.
   *
   * @param tracer the tracer to use in recording spans
   * @param contextManager the context manager to use in handling correlation contexts
   * @param meter the meter to use in recoding measurements
   */
  protected BaseSpanDecorator(
      Tracer tracer, DistributedContextManager contextManager, Meter meter) {
    super();
    this.tracer = tracer;
    this.contextManager = contextManager;
    this.meter = meter;
  }

  @Override
  public SpanScope<Q, P> startSpan(String spanName, C carrier, Q inbound) {
    assert spanName != null;
    long startTimestamp = System.nanoTime();
    Span span = buildSpan(spanName, carrier, startTimestamp);
    addSpanAttributes(span, carrier, inbound);
    Scope scope = tracer.withSpan(span);
    DistributedContext distributedContext =
        buildCorrelationContext(contextManager.getCurrentContext(), carrier, inbound);
    if (isNeedingPropagation()) {
      propagateContexts(carrier, span.getContext(), distributedContext);
    }
    return new ScopeBasedSpanScope<>(
        span,
        scope,
        startTimestamp,
        distributedContext,
        statusTranslator(),
        messageMetadataExtractor(),
        meter,
        spanDurationMeasure(),
        sentBytesMeasure(),
        recdBytesMeasure());
  }

  @Override
  public P callWithTelemetry(String spanName, C carrier, Q inbound, Callable<P> callable)
      throws Exception {
    try (SpanScope<Q, P> scope = startSpan(spanName, carrier, inbound)) {
      P response = null;
      try {
        scope.onMessageSent(inbound);
        response = callable.call();
        scope.onMessageReceived(response);
        scope.onSuccess(response);
        addResultSpanAttributes(scope.getSpan(), null, response);
        return response;
      } catch (Throwable throwable) {
        scope.onError(throwable, response);
        addResultSpanAttributes(scope.getSpan(), throwable, response);
        throw throwable;
      }
    }
  }

  /**
   * Returns the span kind for the created spans.
   *
   * @return the kind
   */
  protected abstract Span.Kind spanKind();

  /**
   * Returns whether the decorator is using the binary propagators.
   *
   * @return binary or not which means using http
   */
  protected boolean isBinaryPropagation() {
    return false;
  }

  /**
   * Returns whether the created spans should have no parent because they are root spans.
   *
   * @return create root spans or not
   */
  protected boolean isNoParent() {
    return false;
  }

  /**
   * Returns whether the parent spans should be extracted from the propagation carrier or not.
   *
   * @return parent span initiated by a remote system or not
   */
  protected abstract boolean isParentRemote();

  /**
   * Returns whether telemetry context info should be injected into propagation carriers or not.
   *
   * @return propagate contexts or not
   */
  protected abstract boolean isNeedingPropagation();

  /**
   * Returns the remote span context extracted from the provided carrier.
   *
   * @param carrier the propagation info carrier
   * @return the span context
   */
  protected abstract SpanContext extractRemoteParentSpan(C carrier);

  /**
   * Returns the propagated correlation context from the provided carrier.
   *
   * @param carrier the propagation info carrier
   * @return the correlation context
   */
  protected abstract DistributedContext extractRemoteCorrelationContext(C carrier);

  /**
   * Injects context data into the provided carrier.
   *
   * @param carrier the propagation info carrier
   * @param span the span context
   * @param corlat the correlation context
   */
  protected abstract void propagateContexts(C carrier, SpanContext span, DistributedContext corlat);

  /**
   * Adds all span attributes available from the environment or inbound object.
   *
   * @param span the active span
   * @param carrier the propagation info carrier
   * @param inbound the request or inbound object
   */
  protected abstract void addSpanAttributes(Span span, C carrier, Q inbound);

  /**
   * Adds span attributes only available from the span results.
   *
   * @param span the active span
   * @param throwable exception which occurred during the span or null if none occurred
   * @param outbound the response or outbound object
   */
  protected abstract void addResultSpanAttributes(Span span, Throwable throwable, P outbound);

  /**
   * Constructs a correlation context for the newly created tracing span.
   *
   * @param currentContext the current correlation context
   * @param carrier the propagation info carrier
   * @param inbound the request or inbound object
   * @return the active context
   */
  protected DistributedContext buildCorrelationContext(
      DistributedContext currentContext, C carrier, Q inbound) {
    return currentContext;
  }

  /**
   * Returns the span status translator.
   *
   * @return the translator
   */
  protected StatusTranslator<P> statusTranslator() {
    return defaultStatusTranslator;
  }

  /**
   * Returns the message metadata extractor.
   *
   * @return the extractor
   */
  protected MessageMetadataExtractor messageMetadataExtractor() {
    return DEFAULT_MESSAGE_METADATA_EXTRACTOR;
  }

  /**
   * Returns the span duration measure or null if durations should not be recorded.
   *
   * @return the measure
   */
  protected MeasureDouble spanDurationMeasure() {
    return null;
  }

  /**
   * Returns the span total sent bytes measure or null if durations should not be recorded.
   *
   * @return the measure
   */
  protected MeasureLong sentBytesMeasure() {
    return null;
  }

  /**
   * Returns the span total received bytes measure or null if durations should not be recorded.
   *
   * @return the masure
   */
  protected MeasureLong recdBytesMeasure() {
    return null;
  }

  /**
   * The tracer being used to record tracing span info.
   *
   * @return the tracer
   */
  protected Tracer getTracer() {
    return tracer;
  }

  /**
   * The context manager being used to manage correlation contexts.
   *
   * @return the context manager
   */
  protected DistributedContextManager getContextManager() {
    return contextManager;
  }

  /**
   * Returns the meter being used to record measurements.
   *
   * @return the meter
   */
  protected Meter getMeter() {
    return meter;
  }

  private Span buildSpan(String spanName, C carrier, long startTimestamp) {
    Span.Builder builder =
        tracer.spanBuilder(spanName).setSpanKind(spanKind()).setStartTimestamp(startTimestamp);
    if (isNoParent()) {
      builder.setNoParent();
    } else if (isParentRemote()) {
      builder.setParent(extractRemoteParentSpan(carrier));
    } else {
      builder.setParent(tracer.getCurrentSpan());
    }
    return builder.startSpan();
  }
}

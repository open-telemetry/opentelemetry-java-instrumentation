/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// Includes work from:
/*
 * Apache Camel Opentracing Component
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Scope;
import org.apache.camel.Exchange;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Utility class for managing active spans as a stack associated with an exchange. */
class ActiveSpanManager {

  private static final String ACTIVE_SPAN_PROPERTY = "OpenTelemetry.activeSpan";

  private static final Logger LOG = LoggerFactory.getLogger(ActiveSpanManager.class);

  private ActiveSpanManager() {}

  public static Span getSpan(Exchange exchange) {
    SpanWithScope spanWithScope = exchange.getProperty(ACTIVE_SPAN_PROPERTY, SpanWithScope.class);
    if (spanWithScope != null) {
      return spanWithScope.getSpan();
    }
    return null;
  }

  /**
   * This method activates the supplied span for the supplied exchange. If an existing span is found
   * for the exchange, this will be pushed onto a stack.
   *
   * @param exchange The exchange
   * @param span The span
   */
  public static void activate(Exchange exchange, Span span, SpanKind spanKind) {

    SpanWithScope parent = exchange.getProperty(ACTIVE_SPAN_PROPERTY, SpanWithScope.class);
    SpanWithScope spanWithScope = SpanWithScope.activate(span, parent, spanKind);
    exchange.setProperty(ACTIVE_SPAN_PROPERTY, spanWithScope);
    LOG.debug("Activated a span: {}", spanWithScope);
  }

  /**
   * This method deactivates an existing active span associated with the supplied exchange. Once
   * deactivated, if a parent span is found associated with the stack for the exchange, it will be
   * restored as the current span for that exchange.
   *
   * @param exchange The exchange
   */
  public static void deactivate(Exchange exchange) {

    SpanWithScope spanWithScope = exchange.getProperty(ACTIVE_SPAN_PROPERTY, SpanWithScope.class);

    if (spanWithScope != null) {
      spanWithScope.deactivate();
      exchange.setProperty(ACTIVE_SPAN_PROPERTY, spanWithScope.getParent());
      LOG.debug("Deactivated span: {}", spanWithScope);
    }
  }

  public static class SpanWithScope {
    @Nullable private final SpanWithScope parent;
    private final Span span;
    private final Scope scope;

    public SpanWithScope(SpanWithScope parent, Span span, Scope scope) {
      this.parent = parent;
      this.span = span;
      this.scope = scope;
    }

    public static SpanWithScope activate(Span span, SpanWithScope parent, SpanKind spanKind) {
      Scope scope = null;
      if (isClientSpan(spanKind)) {
        scope = CamelTracer.TRACER.startClientScope(span);
      } else {
        scope = span.makeCurrent();
      }

      return new SpanWithScope(parent, span, scope);
    }

    private static boolean isClientSpan(SpanKind kind) {
      return (SpanKind.CLIENT.equals(kind) || SpanKind.PRODUCER.equals(kind));
    }

    public SpanWithScope getParent() {
      return parent;
    }

    public Span getSpan() {
      return span;
    }

    public void deactivate() {
      span.end();
      scope.close();
    }

    @Override
    public String toString() {
      return "SpanWithScope [span=" + span + ", scope=" + scope + "]";
    }
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// Includes work from:
/*
 * Copyright 2017-2021 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.opentelemetry.instrumentation.jdbc;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Set;
import java.util.concurrent.TimeUnit;

class JdbcTracingUtils {

  static final AttributeKey<Boolean> SLOW = AttributeKey.booleanKey("slow");

  // TODO is it requires for OTEL?
  static final AttributeKey<Long> SAMPLING_PRIORITY = AttributeKey.longKey("sampling.priority");

  static Span buildSpan(
      String operationName,
      String sql,
      ConnectionInfo connectionInfo,
      boolean withActiveSpanOnly,
      Set<String> ignoreStatements,
      Tracer tracer) {
    if (!JdbcTracing.isTraceEnabled()
        || (withActiveSpanOnly && Span.fromContextOrNull(Context.current()) == null)) {
      // noop span
      return Span.getInvalid();
    } else if (ignoreStatements != null && ignoreStatements.contains(sql)) {
      // noop span
      return Span.getInvalid();
    }

    SpanBuilder spanBuilder = tracer.spanBuilder(operationName).setSpanKind(SpanKind.CLIENT);

    Span span = spanBuilder.startSpan();
    decorate(span, sql, connectionInfo);

    return span;
  }

  static <E extends Exception> void execute(
      String operationName,
      CheckedRunnable<E> runnable,
      String sql,
      ConnectionInfo connectionInfo,
      boolean withActiveSpanOnly,
      Set<String> ignoreStatements,
      Tracer tracer)
      throws E {
    if (!JdbcTracing.isTraceEnabled()
        || (withActiveSpanOnly && Span.fromContextOrNull(Context.current()) == null)) {
      runnable.run();
      return;
    }

    final Span span =
        buildSpan(operationName, sql, connectionInfo, withActiveSpanOnly, ignoreStatements, tracer);
    long startTime =
        (JdbcTracing.getSlowQueryThresholdMs() > 0
                || JdbcTracing.getExcludeFastQueryThresholdMs() > 0)
            ? System.nanoTime()
            : 0;
    try (Scope ignored = span.makeCurrent()) {
      runnable.run();
    } catch (Exception e) {
      JdbcTracingUtils.onError(e, span);
      throw e;
    } finally {
      JdbcTracingUtils.queryThresholdChecks(span, startTime);
      span.end();
    }
  }

  static <T, E extends Exception> T call(
      String operationName,
      CheckedCallable<T, E> callable,
      String sql,
      ConnectionInfo connectionInfo,
      boolean withActiveSpanOnly,
      Set<String> ignoreStatements,
      Tracer tracer)
      throws E {
    if (!JdbcTracing.isTraceEnabled()
        || (withActiveSpanOnly && Span.fromContextOrNull(Context.current()) == null)) {
      return callable.call();
    }

    final Span span =
        buildSpan(operationName, sql, connectionInfo, withActiveSpanOnly, ignoreStatements, tracer);
    long startTime = JdbcTracing.getSlowQueryThresholdMs() > 0 ? System.nanoTime() : 0;
    try (Scope ignored = span.makeCurrent()) {
      return callable.call();
    } catch (Exception e) {
      JdbcTracingUtils.onError(e, span);
      throw e;
    } finally {
      JdbcTracingUtils.queryThresholdChecks(span, startTime);
      span.end();
    }
  }

  private static boolean isNotEmpty(CharSequence s) {
    return s != null && !"".contentEquals(s);
  }

  /** Add tags to span. Skip empty tags to avoid reported NPE in tracers. */
  private static void decorate(Span span, String sql, ConnectionInfo connectionInfo) {
    if (isNotEmpty(sql)) {
      span.setAttribute(SemanticAttributes.DB_STATEMENT, sql);
    }
    if (isNotEmpty(connectionInfo.getDbType())) {
      span.setAttribute(SemanticAttributes.DB_SYSTEM, connectionInfo.getDbType());
    }
    if (isNotEmpty(connectionInfo.getDbPeer())) {
      span.setAttribute(SemanticAttributes.NET_PEER_IP, connectionInfo.getDbPeer());
    }
    if (isNotEmpty(connectionInfo.getDbInstance())) {
      span.setAttribute(SemanticAttributes.DB_NAME, connectionInfo.getDbInstance());
    }
    if (isNotEmpty(connectionInfo.getDbUser())) {
      span.setAttribute(SemanticAttributes.DB_USER, connectionInfo.getDbUser());
    }
    if (isNotEmpty(connectionInfo.getPeerService())) {
      span.setAttribute(SemanticAttributes.PEER_SERVICE, connectionInfo.getPeerService());
    }
  }

  static void onError(Throwable throwable, Span span) {
    span.setStatus(StatusCode.ERROR);

    if (throwable != null) {
      span.recordException(throwable);
    }
  }

  private static void queryThresholdChecks(Span span, long startTime) {
    long completionTime = System.nanoTime() - startTime;
    if (JdbcTracing.getExcludeFastQueryThresholdMs() > 0
        && completionTime
            < TimeUnit.MILLISECONDS.toNanos(JdbcTracing.getExcludeFastQueryThresholdMs())) {
      span.setAttribute(SAMPLING_PRIORITY, 0);
    }
    if (JdbcTracing.getSlowQueryThresholdMs() > 0
        && completionTime > TimeUnit.MILLISECONDS.toNanos(JdbcTracing.getSlowQueryThresholdMs())) {
      span.setAttribute(SLOW, true);
    }
  }

  @FunctionalInterface
  interface CheckedRunnable<E extends Throwable> {

    void run() throws E;
  }

  @FunctionalInterface
  interface CheckedCallable<T, E extends Throwable> {

    T call() throws E;
  }
}

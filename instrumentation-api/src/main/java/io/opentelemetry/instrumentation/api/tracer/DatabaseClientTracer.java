/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import static io.opentelemetry.api.trace.Span.Kind.CLIENT;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.attributes.SemanticAttributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;

public abstract class DatabaseClientTracer<CONNECTION, QUERY> extends BaseTracer {

  protected static final String DB_QUERY = "DB Query";

  protected final Tracer tracer;

  public DatabaseClientTracer() {
    tracer = OpenTelemetry.getGlobalTracer(getInstrumentationName(), getVersion());
  }

  public Span startSpan(CONNECTION connection, QUERY query) {
    String normalizedQuery = normalizeQuery(query);

    Span span =
        tracer
            .spanBuilder(spanName(connection, query, normalizedQuery))
            .setSpanKind(CLIENT)
            .setAttribute(SemanticAttributes.DB_SYSTEM, dbSystem(connection))
            .startSpan();

    if (connection != null) {
      onConnection(span, connection);
      setNetSemanticConvention(span, connection);
    }
    onStatement(span, normalizedQuery);

    return span;
  }

  /**
   * Creates new scoped context with the given span.
   *
   * <p>Attaches new context to the request to avoid creating duplicate client spans.
   */
  @Override
  public Scope startScope(Span span) {
    // TODO we could do this in one go, but TracingContextUtils.CONTEXT_SPAN_KEY is private
    Context clientSpanContext = Context.current().with(CONTEXT_CLIENT_SPAN_KEY, span);
    Context newContext = clientSpanContext.with(span);
    return newContext.makeCurrent();
  }

  @Override
  public Span getCurrentSpan() {
    return Span.current();
  }

  public Span getClientSpan() {
    Context context = Context.current();
    return context.get(CONTEXT_CLIENT_SPAN_KEY);
  }

  @Override
  public void end(Span span) {
    span.end();
  }

  @Override
  public void endExceptionally(Span span, Throwable throwable) {
    onError(span, throwable);
    end(span);
  }

  /** This should be called when the connection is being used, not when it's created. */
  protected Span onConnection(Span span, CONNECTION connection) {
    span.setAttribute(SemanticAttributes.DB_USER, dbUser(connection));
    span.setAttribute(SemanticAttributes.DB_NAME, dbName(connection));
    span.setAttribute(SemanticAttributes.DB_CONNECTION_STRING, dbConnectionString(connection));
    return span;
  }

  @Override
  protected void onError(Span span, Throwable throwable) {
    if (throwable != null) {
      span.setStatus(StatusCode.ERROR);
      addThrowable(
          span, throwable instanceof ExecutionException ? throwable.getCause() : throwable);
    }
  }

  protected void setNetSemanticConvention(Span span, CONNECTION connection) {
    NetPeerUtils.setNetPeer(span, peerAddress(connection));
  }

  protected void onStatement(Span span, String statement) {
    span.setAttribute(SemanticAttributes.DB_STATEMENT, statement);
  }

  protected abstract String normalizeQuery(QUERY query);

  protected abstract String dbSystem(CONNECTION connection);

  protected String dbUser(CONNECTION connection) {
    return null;
  }

  protected String dbName(CONNECTION connection) {
    return null;
  }

  protected String dbConnectionString(CONNECTION connection) {
    return null;
  }

  protected abstract InetSocketAddress peerAddress(CONNECTION connection);

  protected String spanName(CONNECTION connection, QUERY query, String normalizedQuery) {
    if (normalizedQuery != null) {
      return normalizedQuery;
    }

    String result = null;
    if (connection != null) {
      result = dbName(connection);
    }
    return result == null ? DB_QUERY : result;
  }
}

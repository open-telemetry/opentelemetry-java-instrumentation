/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import static io.opentelemetry.api.trace.Span.Kind.CLIENT;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;

public abstract class DatabaseClientTracer<CONNECTION, QUERY> extends BaseTracer {

  protected static final String DB_QUERY = "DB Query";

  protected final Tracer tracer;

  public DatabaseClientTracer() {
    tracer = GlobalOpenTelemetry.getTracer(getInstrumentationName(), getVersion());
  }

  public boolean shouldStartSpan(Context parentContext) {
    return shouldStartSpan(CLIENT, parentContext);
  }

  public Context startSpan(Context parentContext, CONNECTION connection, QUERY query) {
    String normalizedQuery = normalizeQuery(query);

    Span span =
        tracer
            .spanBuilder(spanName(connection, query, normalizedQuery))
            .setParent(parentContext)
            .setSpanKind(CLIENT)
            .setAttribute(SemanticAttributes.DB_SYSTEM, dbSystem(connection))
            .startSpan();

    if (connection != null) {
      onConnection(span, connection);
      setNetSemanticConvention(span, connection);
    }
    onStatement(span, normalizedQuery);

    return withClientSpan(parentContext, span);
  }

  @Override
  public Span getCurrentSpan() {
    return Span.current();
  }

  public void end(Context context) {
    Span.fromContext(context).end();
  }

  public void endExceptionally(Context context, Throwable throwable) {
    Span span = Span.fromContext(context);
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
    NetPeerUtils.INSTANCE.setNetPeer(span, peerAddress(connection));
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

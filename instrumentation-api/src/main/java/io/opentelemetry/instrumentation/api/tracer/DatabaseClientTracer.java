/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

/**
 * Base class for implementing Tracers for database clients.
 *
 * @param <CONNECTION> type of the database connection.
 * @param <STATEMENT> type of the database statement being executed.
 * @param <SANITIZEDSTATEMENT> type of the database statement after sanitization.
 * @deprecated Use {@link io.opentelemetry.instrumentation.api.instrumenter.Instrumenter} and
 *     {@linkplain io.opentelemetry.instrumentation.api.instrumenter.db the database semantic
 *     convention utilities package} instead.
 */
@Deprecated
public abstract class DatabaseClientTracer<CONNECTION, STATEMENT, SANITIZEDSTATEMENT>
    extends BaseTracer {
  private static final String DB_QUERY = "DB Query";

  protected final io.opentelemetry.instrumentation.api.tracer.net.NetPeerAttributes netPeerAttributes;

  protected DatabaseClientTracer(io.opentelemetry.instrumentation.api.tracer.net.NetPeerAttributes netPeerAttributes) {
    this.netPeerAttributes = netPeerAttributes;
  }

  protected DatabaseClientTracer(OpenTelemetry openTelemetry, io.opentelemetry.instrumentation.api.tracer.net.NetPeerAttributes netPeerAttributes) {
    super(openTelemetry);
    this.netPeerAttributes = netPeerAttributes;
  }

  public boolean shouldStartSpan(Context parentContext) {
    return shouldStartSpan(parentContext, CLIENT);
  }

  public Context startSpan(Context parentContext, CONNECTION connection, STATEMENT statement) {
    SANITIZEDSTATEMENT sanitizedStatement = sanitizeStatement(statement);

    SpanBuilder span =
        spanBuilder(parentContext, spanName(connection, statement, sanitizedStatement), CLIENT)
            .setAttribute(SemanticAttributes.DB_SYSTEM, dbSystem(connection));

    if (connection != null) {
      onConnection(span, connection);
      setNetSemanticConvention(span, connection);
    }
    onStatement(span, connection, statement, sanitizedStatement);

    return withClientSpan(parentContext, span.startSpan());
  }

  protected abstract SANITIZEDSTATEMENT sanitizeStatement(STATEMENT statement);

  protected String spanName(
      CONNECTION connection, STATEMENT statement, SANITIZEDSTATEMENT sanitizedStatement) {
    return conventionSpanName(
        dbName(connection), dbOperation(connection, statement, sanitizedStatement), null);
  }

  /**
   * A helper method for constructing the span name formatting according to DB semantic conventions:
   * {@code <db.operation> <db.name><table>}.
   */
  public static String conventionSpanName(
      @Nullable String dbName, @Nullable String operation, @Nullable String table) {
    return conventionSpanName(dbName, operation, table, DB_QUERY);
  }

  /**
   * A helper method for constructing the span name formatting according to DB semantic conventions:
   * {@code <db.operation> <db.name><table>}. If {@code dbName} and {@code operation} are not
   * provided then {@code defaultValue} is returned.
   */
  public static String conventionSpanName(
      @Nullable String dbName,
      @Nullable String operation,
      @Nullable String table,
      String defaultValue) {
    if (operation == null) {
      return dbName == null ? defaultValue : dbName;
    }

    StringBuilder name = new StringBuilder(operation);
    if (dbName != null || table != null) {
      name.append(' ');
    }
    if (dbName != null) {
      name.append(dbName);
      if (table != null) {
        name.append('.');
      }
    }
    if (table != null) {
      name.append(table);
    }
    return name.toString();
  }

  protected abstract String dbSystem(CONNECTION connection);

  /** This should be called when the connection is being used, not when it's created. */
  protected void onConnection(SpanBuilder span, CONNECTION connection) {
    span.setAttribute(SemanticAttributes.DB_USER, dbUser(connection));
    span.setAttribute(SemanticAttributes.DB_NAME, dbName(connection));
    span.setAttribute(SemanticAttributes.DB_CONNECTION_STRING, dbConnectionString(connection));
  }

  protected String dbUser(CONNECTION connection) {
    return null;
  }

  protected String dbName(CONNECTION connection) {
    return null;
  }

  @Nullable
  protected String dbConnectionString(CONNECTION connection) {
    return null;
  }

  protected void setNetSemanticConvention(SpanBuilder span, CONNECTION connection) {
    netPeerAttributes.setNetPeer(span, peerAddress(connection));
  }

  @Nullable
  protected abstract InetSocketAddress peerAddress(CONNECTION connection);

  protected void onStatement(
      SpanBuilder span,
      CONNECTION connection,
      STATEMENT statement,
      SANITIZEDSTATEMENT sanitizedStatement) {
    span.setAttribute(
        SemanticAttributes.DB_STATEMENT, dbStatement(connection, statement, sanitizedStatement));
    span.setAttribute(
        SemanticAttributes.DB_OPERATION, dbOperation(connection, statement, sanitizedStatement));
  }

  protected String dbStatement(
      CONNECTION connection, STATEMENT statement, SANITIZEDSTATEMENT sanitizedStatement) {
    return null;
  }

  protected String dbOperation(
      CONNECTION connection, STATEMENT statement, SANITIZEDSTATEMENT sanitizedStatement) {
    return null;
  }
}

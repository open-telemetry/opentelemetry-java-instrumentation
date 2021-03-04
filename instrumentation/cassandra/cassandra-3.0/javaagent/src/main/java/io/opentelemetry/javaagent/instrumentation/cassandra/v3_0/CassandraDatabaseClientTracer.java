/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import com.datastax.driver.core.ExecutionInfo;
import com.datastax.driver.core.Host;
import com.datastax.driver.core.Session;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.DatabaseClientTracer;
import io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils;
import io.opentelemetry.javaagent.instrumentation.api.db.SqlStatementInfo;
import io.opentelemetry.javaagent.instrumentation.api.db.SqlStatementSanitizer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes.DbSystemValues;
import java.net.InetSocketAddress;
import org.checkerframework.checker.nullness.qual.Nullable;

public class CassandraDatabaseClientTracer
    extends DatabaseClientTracer<Session, String, SqlStatementInfo> {
  private static final CassandraDatabaseClientTracer TRACER = new CassandraDatabaseClientTracer();

  public static CassandraDatabaseClientTracer tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.cassandra-3.0";
  }

  @Override
  protected SqlStatementInfo sanitizeStatement(String statement) {
    return SqlStatementSanitizer.sanitize(statement).mapTable(this::stripKeyspace);
  }

  // account for splitting out the keyspace, <keyspace>.<table>
  @Nullable
  private String stripKeyspace(String table) {
    int i;
    if (table == null || (i = table.indexOf('.')) == -1) {
      return table;
    }
    return table.substring(i + 1);
  }

  @Override
  protected String spanName(
      Session connection, String statement, SqlStatementInfo sanitizedStatement) {
    return conventionSpanName(
        dbName(connection),
        sanitizedStatement.getOperation(),
        sanitizedStatement.getTable(),
        sanitizedStatement.getFullStatement());
  }

  @Override
  protected String dbSystem(Session session) {
    return DbSystemValues.CASSANDRA;
  }

  @Override
  protected void onConnection(SpanBuilder span, Session session) {
    span.setAttribute(SemanticAttributes.DB_CASSANDRA_KEYSPACE, session.getLoggedKeyspace());
    super.onConnection(span, session);
  }

  @Override
  protected String dbName(Session session) {
    return session.getLoggedKeyspace();
  }

  @Override
  protected InetSocketAddress peerAddress(Session session) {
    return null;
  }

  @Override
  protected void onStatement(
      SpanBuilder span, Session connection, String statement, SqlStatementInfo sanitizedStatement) {
    super.onStatement(span, connection, statement, sanitizedStatement);
    String table = sanitizedStatement.getTable();
    if (table != null) {
      span.setAttribute(SemanticAttributes.DB_CASSANDRA_TABLE, table);
    }
  }

  @Override
  protected String dbStatement(
      Session connection, String statement, SqlStatementInfo sanitizedStatement) {
    return sanitizedStatement.getFullStatement();
  }

  @Override
  protected String dbOperation(
      Session connection, String statement, SqlStatementInfo sanitizedStatement) {
    return sanitizedStatement.getOperation();
  }

  public void end(Context context, ExecutionInfo executionInfo) {
    Span span = Span.fromContext(context);
    Host host = executionInfo.getQueriedHost();
    NetPeerUtils.INSTANCE.setNetPeer(span, host.getSocketAddress());
    end(context);
  }
}

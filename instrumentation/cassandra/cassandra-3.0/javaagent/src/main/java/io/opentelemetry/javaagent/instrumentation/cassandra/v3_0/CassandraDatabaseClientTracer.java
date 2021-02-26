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

public class CassandraDatabaseClientTracer
    extends DatabaseClientTracer<Session, String, SqlStatementInfo> {
  private static final CassandraDatabaseClientTracer TRACER = new CassandraDatabaseClientTracer();

  public static CassandraDatabaseClientTracer tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.cassandra";
  }

  @Override
  protected SqlStatementInfo sanitizeStatement(String statement) {
    return SqlStatementSanitizer.sanitize(statement);
  }

  // TODO: use the <operation> <db.name>.<table> naming scheme
  protected String spanName(
      Session connection, String statement, SqlStatementInfo sanitizedStatement) {
    String fullStatement = sanitizedStatement.getFullStatement();
    if (fullStatement != null) {
      return fullStatement;
    }

    String result = null;
    if (connection != null) {
      result = dbName(connection);
    }
    return result == null ? DB_QUERY : result;
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
  protected String dbStatement(
      Session connection, String statement, SqlStatementInfo sanitizedStatement) {
    return sanitizedStatement.getFullStatement();
  }

  public void end(Context context, ExecutionInfo executionInfo) {
    Span span = Span.fromContext(context);
    Host host = executionInfo.getQueriedHost();
    NetPeerUtils.INSTANCE.setNetPeer(span, host.getSocketAddress());
    end(context);
  }
}

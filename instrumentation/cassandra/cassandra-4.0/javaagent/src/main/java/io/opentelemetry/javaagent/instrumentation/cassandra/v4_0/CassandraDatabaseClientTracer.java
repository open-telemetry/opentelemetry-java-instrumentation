/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import static io.opentelemetry.javaagent.instrumentation.cassandra.v4_0.CassandraTableNameExtractor.extractTableNameFromQuery;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.cql.Statement;
import com.datastax.oss.driver.api.core.metadata.Node;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.attributes.SemanticAttributes;
import io.opentelemetry.api.trace.attributes.SemanticAttributes.DbSystemValues;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.DatabaseClientTracer;
import io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils;
import io.opentelemetry.javaagent.instrumentation.api.db.SqlStatementSanitizer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes.DbSystemValues;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class CassandraDatabaseClientTracer extends DatabaseClientTracer<CqlSession, String> {

  private static final CassandraDatabaseClientTracer TRACER = new CassandraDatabaseClientTracer();

  public static CassandraDatabaseClientTracer tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.cassandra";
  }

  @Override
  protected String normalizeQuery(String query) {
    return SqlStatementSanitizer.sanitize(query).getFullStatement();
  }

  @Override
  protected String dbSystem(CqlSession session) {
    return DbSystemValues.CASSANDRA;
  }

  @Override
  protected String dbName(CqlSession session) {
    return session.getKeyspace().map(CqlIdentifier::toString).orElse(null);
  }

  @Override
  protected InetSocketAddress peerAddress(CqlSession cqlSession) {
    return null;
  }

  @Override
  protected Span onConnection(Span span, CqlSession cqlSession) {
    span = super.onConnection(span, cqlSession);
    DriverExecutionProfile config = cqlSession.getContext().getConfig().getDefaultProfile();
    // may be overwritten by statement, but take the default for now
    int pageSize = config.getInt(DefaultDriverOption.REQUEST_PAGE_SIZE);
    if (pageSize > 0) {
      span.setAttribute(SemanticAttributes.DB_CASSANDRA_PAGE_SIZE, pageSize);
    }
    // may be overwritten by statement, but take the default for now
    span.setAttribute(
        SemanticAttributes.DB_CASSANDRA_CONSISTENCY_LEVEL,
        config.getString(DefaultDriverOption.REQUEST_CONSISTENCY));
    return span;
  }

  public void onResponse(Context context, ExecutionInfo executionInfo) {
    Span span = Span.fromContext(context);
    Node coordinator = executionInfo.getCoordinator();
    if (coordinator != null) {
      SocketAddress socketAddress = coordinator.getEndPoint().resolve();
      if (socketAddress instanceof InetSocketAddress) {
        NetPeerUtils.INSTANCE.setNetPeer(span, ((InetSocketAddress) socketAddress));
      }
      if (coordinator.getDatacenter() != null) {
        span.setAttribute(
            SemanticAttributes.DB_CASSANDRA_COORDINATOR_DC, coordinator.getDatacenter());
      }
      if (coordinator.getHostId() != null) {
        span.setAttribute(
            SemanticAttributes.DB_CASSANDRA_COORDINATOR_ID, coordinator.getHostId().toString());
      }
    }
    span.setAttribute(
        SemanticAttributes.DB_CASSANDRA_SPECULATIVE_EXECUTION_COUNT,
        executionInfo.getSpeculativeExecutionCount());

    Statement<?> statement = executionInfo.getStatement();
    // override connection default if present
    if (statement.getConsistencyLevel() != null) {
      span.setAttribute(
          SemanticAttributes.DB_CASSANDRA_CONSISTENCY_LEVEL,
          statement.getConsistencyLevel().name());
    }
    // override connection default if present
    if (statement.getPageSize() > 0) {
      span.setAttribute(SemanticAttributes.DB_CASSANDRA_PAGE_SIZE, statement.getPageSize());
    }
    span.setAttribute(SemanticAttributes.DB_CASSANDRA_IDEMPOTENCE, isIdempotent(statement));
  }

  @Override
  protected void onStatement(Span span, String statement) {
    super.onStatement(span, statement);
    String table = extractTableNameFromQuery(statement);
    if (table != null) {
      span.setAttribute(SemanticAttributes.DB_CASSANDRA_TABLE, table);
    }
  }

  private static boolean isIdempotent(Statement<?> statement) {
    return Boolean.TRUE.equals(statement.isIdempotent());
  }
}

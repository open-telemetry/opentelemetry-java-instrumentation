/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.DriverException;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.cql.Statement;
import com.datastax.oss.driver.api.core.metadata.Node;
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
import java.net.SocketAddress;
import org.checkerframework.checker.nullness.qual.Nullable;

public class CassandraDatabaseClientTracer
    extends DatabaseClientTracer<CqlSession, String, SqlStatementInfo> {

  private static final CassandraDatabaseClientTracer TRACER = new CassandraDatabaseClientTracer();

  public static CassandraDatabaseClientTracer tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.cassandra-4.0";
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
      CqlSession connection, String statement, SqlStatementInfo sanitizedStatement) {
    return conventionSpanName(
        dbName(connection),
        sanitizedStatement.getOperation(),
        sanitizedStatement.getTable(),
        sanitizedStatement.getFullStatement());
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
  protected void onStatement(
      SpanBuilder span,
      CqlSession connection,
      String statement,
      SqlStatementInfo sanitizedStatement) {
    super.onStatement(span, connection, statement, sanitizedStatement);
    String table = sanitizedStatement.getTable();
    if (table != null) {
      span.setAttribute(SemanticAttributes.DB_CASSANDRA_TABLE, table);
    }
  }

  @Override
  protected String dbStatement(
      CqlSession connection, String statement, SqlStatementInfo sanitizedStatement) {
    return sanitizedStatement.getFullStatement();
  }

  @Override
  protected String dbOperation(
      CqlSession connection, String statement, SqlStatementInfo sanitizedStatement) {
    return sanitizedStatement.getOperation();
  }

  public void onResponse(Context context, CqlSession cqlSession, ExecutionInfo executionInfo) {
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
    DriverExecutionProfile config = cqlSession.getContext().getConfig().getDefaultProfile();
    if (statement.getConsistencyLevel() != null) {
      span.setAttribute(
          SemanticAttributes.DB_CASSANDRA_CONSISTENCY_LEVEL,
          statement.getConsistencyLevel().name());
    } else {
      span.setAttribute(
          SemanticAttributes.DB_CASSANDRA_CONSISTENCY_LEVEL,
          config.getString(DefaultDriverOption.REQUEST_CONSISTENCY));
    }
    if (statement.getPageSize() > 0) {
      span.setAttribute(SemanticAttributes.DB_CASSANDRA_PAGE_SIZE, statement.getPageSize());
    } else {
      int pageSize = config.getInt(DefaultDriverOption.REQUEST_PAGE_SIZE);
      if (pageSize > 0) {
        span.setAttribute(SemanticAttributes.DB_CASSANDRA_PAGE_SIZE, pageSize);
      }
    }
    if (statement.isIdempotent() != null) {
      span.setAttribute(SemanticAttributes.DB_CASSANDRA_IDEMPOTENCE, statement.isIdempotent());
    } else {
      span.setAttribute(
          SemanticAttributes.DB_CASSANDRA_IDEMPOTENCE,
          config.getBoolean(DefaultDriverOption.REQUEST_DEFAULT_IDEMPOTENCE));
    }
  }

  /** Use this method instead of {@link #endExceptionally(Context, Throwable)}. */
  public void endExceptionally(Context context, final Throwable throwable, CqlSession cqlSession) {
    DriverException e = null;
    if (throwable instanceof DriverException) {
      e = (DriverException) throwable;
    } else if (throwable.getCause() instanceof DriverException) {
      e = (DriverException) throwable.getCause();
    }
    if (e != null && e.getExecutionInfo() != null) {
      onResponse(context, cqlSession, e.getExecutionInfo());
    }
    super.endExceptionally(context, throwable);
  }

  /** Use {@link #endExceptionally(Context, Throwable, CqlSession)}. */
  @Override
  public void endExceptionally(Context context, final Throwable throwable) {
    throw new IllegalStateException(
        "use the endExceptionally method with a CqlSession in CassandraDatabaseClientTracer");
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import com.datastax.driver.core.ExecutionInfo;
import com.datastax.driver.core.Host;
import com.datastax.driver.core.Session;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.DatabaseClientTracer;
import io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils;
import io.opentelemetry.javaagent.instrumentation.api.db.SqlStatementSanitizer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes.DbSystemValues;
import java.net.InetSocketAddress;

public class CassandraDatabaseClientTracer extends DatabaseClientTracer<Session, String> {
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
  protected String dbSystem(Session session) {
    return DbSystemValues.CASSANDRA;
  }

  @Override
  protected String dbName(Session session) {
    return session.getLoggedKeyspace();
  }

  @Override
  protected Span onConnection(Span span, Session session) {
    span.setAttribute(SemanticAttributes.DB_CASSANDRA_KEYSPACE, session.getLoggedKeyspace());
    return super.onConnection(span, session);
  }

  @Override
  protected InetSocketAddress peerAddress(Session session) {
    return null;
  }

  public void end(Context context, ExecutionInfo executionInfo) {
    Span span = Span.fromContext(context);
    Host host = executionInfo.getQueriedHost();
    NetPeerUtils.INSTANCE.setNetPeer(span, host.getSocketAddress());
    end(context);
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import com.datastax.driver.core.ExecutionInfo;
import com.datastax.driver.core.Host;
import com.datastax.driver.core.Session;
import io.opentelemetry.instrumentation.api.tracer.DatabaseClientTracer;
import io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils;
import io.opentelemetry.javaagent.instrumentation.api.db.DbSystem;
import io.opentelemetry.javaagent.instrumentation.api.db.cassandra.CassandraQueryNormalizer;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;

public class CassandraDatabaseClientTracer extends DatabaseClientTracer<Session, String> {
  public static final CassandraDatabaseClientTracer TRACER = new CassandraDatabaseClientTracer();

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.cassandra";
  }

  @Override
  protected String normalizeQuery(String query) {
    return CassandraQueryNormalizer.normalize(query);
  }

  @Override
  protected String dbSystem(Session session) {
    return DbSystem.CASSANDRA;
  }

  @Override
  protected String dbName(Session session) {
    return session.getLoggedKeyspace();
  }

  @Override
  protected Span onConnection(Span span, Session session) {
    span.setAttribute(SemanticAttributes.CASSANDRA_KEYSPACE, session.getLoggedKeyspace());
    return super.onConnection(span, session);
  }

  @Override
  protected InetSocketAddress peerAddress(Session session) {
    return null;
  }

  public void end(Span span, ExecutionInfo executionInfo) {
    Host host = executionInfo.getQueriedHost();
    NetPeerUtils.setNetPeer(span, host.getSocketAddress());
    end(span);
  }
}

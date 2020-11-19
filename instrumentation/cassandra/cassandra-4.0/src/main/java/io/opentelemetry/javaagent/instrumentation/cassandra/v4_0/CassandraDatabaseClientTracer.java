/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.metadata.Node;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.api.tracer.DatabaseClientTracer;
import io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils;
import io.opentelemetry.javaagent.instrumentation.api.db.DbSystem;
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
    return CassandraQueryNormalizer.normalize(query);
  }

  @Override
  protected String dbSystem(CqlSession session) {
    return DbSystem.CASSANDRA;
  }

  @Override
  protected String dbName(CqlSession session) {
    return session.getKeyspace().map(CqlIdentifier::toString).orElse(null);
  }

  @Override
  protected InetSocketAddress peerAddress(CqlSession cqlSession) {
    return null;
  }

  public void onResponse(Span span, ExecutionInfo executionInfo) {
    Node coordinator = executionInfo.getCoordinator();
    if (coordinator != null) {
      SocketAddress socketAddress = coordinator.getEndPoint().resolve();
      if (socketAddress instanceof InetSocketAddress) {
        NetPeerUtils.setNetPeer(span, ((InetSocketAddress) socketAddress));
      }
    }
  }
}

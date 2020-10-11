/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.cassandra.v4_0;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.metadata.Node;
import io.opentelemetry.instrumentation.api.tracer.DatabaseClientTracer;
import io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils;
import io.opentelemetry.instrumentation.auto.api.jdbc.DbSystem;
import io.opentelemetry.trace.Span;
import java.net.InetSocketAddress;
import java.util.Optional;

public class CassandraDatabaseClientTracer extends DatabaseClientTracer<CqlSession, String> {
  public static final CassandraDatabaseClientTracer TRACER = new CassandraDatabaseClientTracer();

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.cassandra-4.0";
  }

  @Override
  protected String normalizeQuery(String query) {
    return query;
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
      Optional<InetSocketAddress> address = coordinator.getBroadcastRpcAddress();
      address.ifPresent(inetSocketAddress -> NetPeerUtils.setNetPeer(span, inetSocketAddress));
    }
  }
}

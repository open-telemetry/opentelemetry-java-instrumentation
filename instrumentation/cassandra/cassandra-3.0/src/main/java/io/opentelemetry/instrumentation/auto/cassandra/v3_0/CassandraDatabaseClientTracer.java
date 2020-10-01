/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.auto.cassandra.v3_0;

import com.datastax.driver.core.ExecutionInfo;
import com.datastax.driver.core.Host;
import com.datastax.driver.core.Session;
import io.opentelemetry.instrumentation.api.tracer.DatabaseClientTracer;
import io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils;
import io.opentelemetry.instrumentation.auto.api.jdbc.DbSystem;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;

public class CassandraDatabaseClientTracer extends DatabaseClientTracer<Session, String> {
  public static final CassandraDatabaseClientTracer TRACER = new CassandraDatabaseClientTracer();

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.cassandra-3.0";
  }

  @Override
  protected String normalizeQuery(String query) {
    return query;
  }

  @Override
  protected String dbSystem(Session session) {
    return DbSystem.CASSANDRA;
  }

  @Override
  protected String dbName(Session session) {
    return null;
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
